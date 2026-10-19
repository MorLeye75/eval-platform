package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.dto.soumission.SoumissionDetailResponse;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.TempsEcouleException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration sur H2 (données de DataInitializer) : vérifient le comportement transactionnel
 * réel (rollback, proxies Hibernate, correction après commit) que les tests Mockito ne couvrent pas.
 */
@SpringBootTest
class SoumissionIntegrationTest {

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private AlerteAntiTricheRepository alerteAntiTricheRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String emailEtudiant;
    private Long evalQcmId;
    private Long evalCodeId;

    @BeforeEach
    void setUp() {
        // Un étudiant neuf par test : une seule soumission par (évaluation, étudiant)
        String suffixe = UUID.randomUUID().toString();
        emailEtudiant = "etu-" + suffixe + "@ept.sn";
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Etudiant", emailEtudiant, "x", "ETU-" + suffixe, dic2));

        List<Evaluation> evaluations = evaluationRepository.findAll();
        evalQcmId = evaluations.stream().filter(e -> e.getTitre().contains("QCM")).findFirst().orElseThrow().getId();
        evalCodeId = evaluations.stream().filter(e -> e.getTitre().contains("code")).findFirst().orElseThrow().getId();
    }

    @Test
    @DisplayName("DS-01 (Cas critique) : le verrouillage et l'alerte sont bien persistés malgré l'exception")
    void soumissionTardive_EstPersisteeMalgreException() throws InterruptedException {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        transactionTemplate.executeWithoutResult(tx -> {
            Soumission s = soumissionRepository.findById(soumissionId).orElseThrow();
            s.setDateDebut(LocalDateTime.now().minusMinutes(60));
        });

        assertThatThrownBy(() -> soumissionService.soumettreCopie(soumissionId, emailEtudiant))
                .isInstanceOf(TempsEcouleException.class);

        assertThat(soumissionRepository.findById(soumissionId).orElseThrow().getStatut())
                .isNotEqualTo(StatutSoumission.EN_COURS);
        assertThat(alerteAntiTricheRepository.findBySoumissionId(soumissionId)).hasSize(1);
        // La copie figée est corrigée comme les autres
        assertThat(attendreStatut(soumissionId, StatutSoumission.EVALUE)).isEqualTo(StatutSoumission.EVALUE);
    }

    @Test
    @DisplayName("DS-01 (Cas normal) : la correction automatique QCM note la copie après soumission")
    void soumission_QcmCorrigeAutomatiquement() throws InterruptedException {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(reponsesCorrectes()), emailEtudiant);

        soumissionService.soumettreCopie(soumissionId, emailEtudiant);

        assertThat(attendreStatut(soumissionId, StatutSoumission.EVALUE)).isEqualTo(StatutSoumission.EVALUE);

        Double note = transactionTemplate.execute(tx ->
                soumissionRepository.findById(soumissionId).orElseThrow().getNoteFinale());
        assertThat(note).isEqualTo(5.0);
    }

    @Test
    @DisplayName("DS-02 : une réponse à une question d'une autre évaluation est rejetée")
    void sauvegarde_QuestionAutreEvaluation_Rejetee() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        Long questionCodeId = questionRepository.findByEvaluationIdOrderByOrdreAsc(evalCodeId).get(0).getId();

        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(questionCodeId, null, "return true;", null)));

        assertThatThrownBy(() -> soumissionService.sauvegarderReponses(soumissionId, request, emailEtudiant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DS-02 : un choix appartenant à une autre question est rejeté")
    void sauvegarde_ChoixAutreQuestion_Rejete() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        List<Long> choixQ2 = choixIds(1);
        Long q1Id = questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQcmId).get(0).getId();

        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(q1Id, null, null, List.of(choixQ2.get(0)))));

        assertThatThrownBy(() -> soumissionService.sauvegarderReponses(soumissionId, request, emailEtudiant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Consultation : l'enseignant de l'évaluation peut voir la copie, un autre étudiant non")
    void getDetail_ControleParRole() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();

        SoumissionDetailResponse detail = soumissionService.getDetail(soumissionId, "prof@ept.sn");
        assertThat(detail.getId()).isEqualTo(soumissionId);

        assertThatThrownBy(() -> soumissionService.getDetail(soumissionId, "etudiant@ept.sn"))
                .isInstanceOf(AccesRefuseException.class);
    }

    @Test
    @DisplayName("Quiz chronométré : les choix cochés sont enregistrés et corrigés automatiquement")
    void quizChronometre_SauvegardeEtCorrection() throws InterruptedException {
        Long evalQuizId = creerEvaluationQuiz();
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQuizId, emailEtudiant).getId();

        List<ReponseItemDto> reponses = transactionTemplate.execute(tx -> {
            QuestionQCM quiz = (QuestionQCM) questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQuizId).get(0);
            Long bonChoix = quiz.getChoix().stream().filter(ChoixReponse::getEstCorrect).findFirst().orElseThrow().getId();
            return List.of(new ReponseItemDto(quiz.getId(), null, null, List.of(bonChoix)));
        });
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(reponses), emailEtudiant);

        // Les choix doivent revenir dans le détail de la copie (et non être perdus dans une réponse texte)
        SoumissionDetailResponse detail = soumissionService.getDetail(soumissionId, emailEtudiant);
        assertThat(detail.getQuestions().get(0).getReponseActuelle().getChoixIds()).hasSize(1);

        soumissionService.soumettreCopie(soumissionId, emailEtudiant);

        assertThat(attendreStatut(soumissionId, StatutSoumission.EVALUE)).isEqualTo(StatutSoumission.EVALUE);
        assertThat(soumissionRepository.findById(soumissionId).orElseThrow().getNoteFinale()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Tâche planifiée : une copie abandonnée après expiration est clôturée puis corrigée")
    void cloture_CopieAbandonnee() throws InterruptedException {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(reponsesCorrectes()), emailEtudiant);
        LocalDateTime debut = LocalDateTime.now().minusMinutes(60).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("update soumissions set date_debut = ? where id = ?", debut, soumissionId);

        assertThat(soumissionService.cloturerCopiesExpirees()).isGreaterThanOrEqualTo(1);

        assertThat(attendreStatut(soumissionId, StatutSoumission.EVALUE)).isEqualTo(StatutSoumission.EVALUE);
        Soumission soumission = soumissionRepository.findById(soumissionId).orElseThrow();
        assertThat(soumission.getDateRemise()).isEqualTo(debut.plusMinutes(45)); // figée à la fin théorique
        assertThat(soumission.getNoteFinale()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Tâche planifiée : une copie en cours dans les temps n'est pas clôturée")
    void cloture_CopieDansLesTemps_Intacte() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();

        soumissionService.cloturerCopiesExpirees();

        assertThat(soumissionRepository.findById(soumissionId).orElseThrow().getStatut()).isEqualTo(StatutSoumission.EN_COURS);
    }

    @Test
    @DisplayName("Tâche planifiée : une copie restée SOUMIS (correction perdue) est corrigée à la relance")
    void relance_CorrectionBloquee() throws InterruptedException {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        // Simule une correction jamais exécutée : statut SOUMIS, dernière modification il y a 10 min
        jdbcTemplate.update("update soumissions set statut = 'SOUMIS', date_modification = ? where id = ?",
                LocalDateTime.now().minusMinutes(10), soumissionId);

        assertThat(soumissionService.relancerCorrectionsBloquees()).isGreaterThanOrEqualTo(1);

        assertThat(attendreStatut(soumissionId, StatutSoumission.EVALUE)).isEqualTo(StatutSoumission.EVALUE);
    }

    @Test
    @DisplayName("Démarrage : deux appels simultanés renvoient la même copie, sans erreur")
    void demarrage_Simultane_MemeCopie() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Plusieurs essais avec un nouvel étudiant à chaque fois : une course n'est pas reproductible à coup sûr
            for (int essai = 0; essai < 5; essai++) {
                String email = "etu-concurrent-" + UUID.randomUUID() + "@ept.sn";
                utilisateurRepository.save(new Etudiant("Test", "Concurrent", email, "x", "ETU-" + UUID.randomUUID(),
                        promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));

                CountDownLatch depart = new CountDownLatch(1);
                Callable<Long> demarrer = () -> {
                    depart.await();
                    return soumissionService.demarrerOuReprendre(evalQcmId, email).getId();
                };
                Future<Long> premier = pool.submit(demarrer);
                Future<Long> second = pool.submit(demarrer);
                depart.countDown();

                assertThat(premier.get()).isEqualTo(second.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Audit : un utilisateur créé reçoit sa date de création et son créateur")
    void audit_Utilisateur() {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(emailEtudiant).orElseThrow();

        assertThat(utilisateur.getDateCreation()).isNotNull();
        assertThat(utilisateur.getCreePar()).isEqualTo("SYSTEM"); // créé hors requête authentifiée
    }

    /** Évaluation ouverte composée d'un seul quiz chronométré (4 points). */
    private Long creerEvaluationQuiz() {
        return transactionTemplate.execute(tx -> {
            Evaluation evaluation = new Evaluation();
            evaluation.setTitre("Quiz " + UUID.randomUUID());
            evaluation.setDureeStandard(30);
            evaluation.setDateOuverture(LocalDateTime.now().minusHours(1));
            evaluation.setDateFermeture(LocalDateTime.now().plusHours(2));
            evaluation.setStatut(StatutEvaluation.EN_COURS);
            evaluation.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            evaluation.setPromotions(List.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));

            QuestionQuiz quiz = new QuestionQuiz("Complexité d'une recherche dichotomique ?", 1, 4.0, false, 30);
            quiz.addChoix(new ChoixReponse("O(n)", false));
            quiz.addChoix(new ChoixReponse("O(log n)", true));
            evaluation.addQuestion(quiz);
            return evaluationRepository.save(evaluation).getId();
        });
    }

    /** La correction est asynchrone : attend au plus 5 s que la copie atteigne le statut voulu. */
    private StatutSoumission attendreStatut(Long soumissionId, StatutSoumission attendu) throws InterruptedException {
        StatutSoumission statut = null;
        for (int i = 0; i < 50 && statut != attendu; i++) {
            Thread.sleep(100);
            statut = soumissionRepository.findById(soumissionId).orElseThrow().getStatut();
        }
        return statut;
    }

    /** Bonnes réponses de l'évaluation QCM initialisée par DataInitializer. */
    private List<ReponseItemDto> reponsesCorrectes() {
        return transactionTemplate.execute(tx ->
                questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQcmId).stream()
                        .map(q -> new ReponseItemDto(q.getId(), null, null,
                                ((QuestionQCM) q).getChoix().stream()
                                        .filter(ChoixReponse::getEstCorrect)
                                        .map(ChoixReponse::getId)
                                        .toList()))
                        .toList());
    }

    private List<Long> choixIds(int indexQuestion) {
        return transactionTemplate.execute(tx -> {
            QuestionQCM q = (QuestionQCM) questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQcmId).get(indexQuestion);
            return q.getChoix().stream().map(ChoixReponse::getId).toList();
        });
    }
}
