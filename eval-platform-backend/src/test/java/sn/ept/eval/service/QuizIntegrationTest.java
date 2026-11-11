package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.soumission.QuestionExamenDto;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.dto.soumission.SoumissionDetailResponse;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quiz chronométré : le serveur impose l'ordre des questions et le temps de chacune (pas de retour en arrière,
 * pas de réponse en avance ni hors délai). Base H2 propre à cette classe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quiz;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class QuizIntegrationTest {

    private static final int DUREE_QUESTION = 30;

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private ReponseEtudiantRepository reponseEtudiantRepository;

    @Autowired
    private ClotureCopiesService clotureCopiesService;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;
    private Long evaluationId;
    private List<Long> questionIds;
    /** Premier choix de chaque question (dans l'ordre). */
    private List<Long> choix;

    /** Quiz ouvert de 3 questions de 30 s, et un étudiant de la promotion. */
    @BeforeEach
    void setUp() {
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        evaluationId = transactionTemplate.execute(tx -> {
            Evaluation evaluation = new Evaluation();
            evaluation.setTitre("Quiz " + UUID.randomUUID());
            evaluation.setDureeStandard(10);
            evaluation.setDateOuverture(LocalDateTime.now().minusHours(1));
            evaluation.setDateFermeture(LocalDateTime.now().plusHours(2));
            evaluation.setStatut(StatutEvaluation.EN_COURS);
            evaluation.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            evaluation.getPromotions().add(dic2);
            for (int i = 1; i <= 3; i++) {
                QuestionQuiz q = new QuestionQuiz("Question " + i, i, 1.0, false, DUREE_QUESTION);
                q.addChoix(new ChoixReponse("Bonne " + i, true));
                q.addChoix(new ChoixReponse("Mauvaise " + i, false));
                evaluation.addQuestion(q);
            }
            return evaluationRepository.save(evaluation).getId();
        });
        transactionTemplate.executeWithoutResult(tx -> {
            List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(evaluationId);
            questionIds = questions.stream().map(Question::getId).toList();
            choix = questions.stream().map(q -> ((QuestionQCM) q).getChoix().get(0).getId()).toList();
        });
        String suffixe = UUID.randomUUID().toString();
        email = "etu-" + suffixe + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Quiz", email, "x", "ETU-" + suffixe, dic2));
    }

    private void repondre(Long soumissionId, int indexQuestion) {
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(questionIds.get(indexQuestion), null, null, List.of(choix.get(indexQuestion))))), email);
    }

    private boolean aUneReponse(Long soumissionId, int indexQuestion) {
        return transactionTemplate.execute(tx -> reponseEtudiantRepository.findBySoumissionId(soumissionId).stream()
                .anyMatch(r -> r.getQuestion().getId().equals(questionIds.get(indexQuestion))
                        && !((ReponseQCM) r).getChoixCoches().isEmpty()));
    }

    /** Recule le début de la copie (et de la question en cours) : simule le temps écoulé depuis le démarrage. */
    private void reculer(Long soumissionId, int secondes) {
        jdbcTemplate.update("update soumissions set date_debut = ? where id = ?",
                LocalDateTime.now().minusSeconds(secondes), soumissionId);
    }

    @Test
    @DisplayName("Seule la question en cours est envoyée au navigateur (les suivantes ne peuvent pas être lues à l'avance)")
    void seuleLaQuestionEnCoursEstEnvoyee() {
        SoumissionDetailResponse copie = soumissionService.demarrerOuReprendre(evaluationId, email);

        assertThat(copie.getQuestions()).extracting(QuestionExamenDto::getId).containsExactly(questionIds.get(0));
    }

    @Test
    @DisplayName("Répondre en avance à une question future : la réponse n'est pas enregistrée")
    void reponseEnAvance_Ignoree() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();

        repondre(soumissionId, 1);

        assertThat(aUneReponse(soumissionId, 1)).isFalse();
    }

    @Test
    @DisplayName("Question dont le temps est écoulé : elle ne peut plus être modifiée, la suivante est en cours")
    void questionExpiree_NonModifiable() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        reculer(soumissionId, DUREE_QUESTION + 5); // question 1 : 30 s + marge écoulées

        repondre(soumissionId, 0);

        assertThat(aUneReponse(soumissionId, 0)).isFalse();
        assertThat(soumissionService.demarrerOuReprendre(evaluationId, email).getQuestions())
                .extracting(QuestionExamenDto::getId).containsExactly(questionIds.get(1));
    }

    @Test
    @DisplayName("Position dans le quiz et durée de la question envoyées au navigateur")
    void etatDuQuiz() {
        SoumissionDetailResponse copie = soumissionService.demarrerOuReprendre(evaluationId, email);

        assertThat(copie.getQuiz().indexQuestion()).isZero();
        assertThat(copie.getQuiz().nbQuestions()).isEqualTo(3);
        assertThat(copie.getQuiz().dureeQuestion()).isEqualTo(DUREE_QUESTION);
        assertThat(copie.getQuiz().tempsRestantQuestionSecondes()).isBetween(25L, (long) DUREE_QUESTION);
        assertThat(copie.getQuestions().get(0).getDureeQuestion()).isEqualTo(DUREE_QUESTION);
    }

    @Test
    @DisplayName("Question suivante : la copie avance une seule fois (double clic), la question passée n'est plus modifiable")
    void questionSuivante_SansRetourEnArriere() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        repondre(soumissionId, 0);

        SoumissionDetailResponse apres = soumissionService.passerQuestionSuivante(soumissionId, 0, email);
        SoumissionDetailResponse doubleClic = soumissionService.passerQuestionSuivante(soumissionId, 0, email);

        assertThat(apres.getQuestions()).extracting(QuestionExamenDto::getId).containsExactly(questionIds.get(1));
        assertThat(doubleClic.getQuiz().indexQuestion()).isEqualTo(1);
        assertThat(aUneReponse(soumissionId, 0)).isTrue(); // réponse donnée à temps conservée

        // Tentative de retour en arrière : décocher la question 1
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(questionIds.get(0), null, null, List.of()))), email);
        assertThat(aUneReponse(soumissionId, 0)).isTrue();

        repondre(soumissionId, 1);
        assertThat(aUneReponse(soumissionId, 1)).isTrue();
    }

    @Test
    @DisplayName("Étudiant absent (page fermée) : les questions dont le temps est écoulé sont closes à son retour")
    void absence_QuestionsCloses() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        reculer(soumissionId, 2 * DUREE_QUESTION + 10); // questions 1 et 2 écoulées

        SoumissionDetailResponse reprise = soumissionService.demarrerOuReprendre(evaluationId, email);

        assertThat(reprise.getQuiz().indexQuestion()).isEqualTo(2);
        assertThat(reprise.getQuestions()).extracting(QuestionExamenDto::getId).containsExactly(questionIds.get(2));
    }

    @Test
    @DisplayName("Toutes les questions passées : plus aucune question envoyée, le quiz est terminé")
    void quizTermine() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        soumissionService.passerQuestionSuivante(soumissionId, 0, email);
        soumissionService.passerQuestionSuivante(soumissionId, 1, email);

        SoumissionDetailResponse fin = soumissionService.passerQuestionSuivante(soumissionId, 2, email);

        assertThat(fin.getQuiz().indexQuestion()).isEqualTo(3);
        assertThat(fin.getQuestions()).isEmpty();
    }

    @Test
    @DisplayName("Quiz de démonstration : présent une seule fois, et un étudiant le démarre sur sa première question")
    void quizDeDemonstration() {
        List<Evaluation> quizDemo = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().equals("Bases de données — Quiz chronométré")).toList();
        assertThat(quizDemo).hasSize(1);

        SoumissionDetailResponse copie = soumissionService.demarrerOuReprendre(quizDemo.get(0).getId(), email);

        assertThat(copie.getQuiz().nbQuestions()).isEqualTo(3);
        assertThat(copie.getQuiz().indexQuestion()).isZero();
        assertThat(copie.getQuiz().dureeQuestion()).isEqualTo(30);
        assertThat(copie.getQuestions()).hasSize(1); // seule la question en cours est envoyée
    }

    /** Fin du quiz de setUp : 3 questions de 30 s. Bien avant la fin de la durée standard (10 min). */
    private static final int DUREE_QUIZ = 3 * DUREE_QUESTION;

    private Soumission copie(Long soumissionId) {
        return transactionTemplate.execute(tx -> soumissionRepository.findById(soumissionId).orElseThrow());
    }

    @Test
    @DisplayName("Quiz abandonné : la copie est clôturée à la fin du quiz + 2 min, pas à la fin de la durée standard")
    void quizAbandonne_ClotureALaFinDuQuiz() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        reculer(soumissionId, DUREE_QUIZ + TempsEpreuveService.MARGE_TOLERANCE_MINUTES * 60 + 5);

        clotureCopiesService.cloturerCopiesExpirees();

        Soumission copie = copie(soumissionId);
        assertThat(copie.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        // Remise datée de la fin réelle du quiz (début + 90 s), comme une remise à l'heure
        assertThat(copie.getDateRemise()).isCloseTo(copie.getDateDebut().plusSeconds(DUREE_QUIZ),
                org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Quiz abandonné encore dans la marge de 2 min après sa fin : la copie reste ouverte")
    void quizAbandonne_DansLaMarge_PasCloture() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        reculer(soumissionId, DUREE_QUIZ + 60);

        clotureCopiesService.cloturerCopiesExpirees();

        assertThat(copie(soumissionId).getStatut()).isEqualTo(StatutSoumission.EN_COURS);
    }

    @Test
    @DisplayName("Questions passées en avance puis page fermée : la fin du quiz est avancée d'autant")
    void questionsPasseesEnAvance_FinAvancee() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        soumissionService.passerQuestionSuivante(soumissionId, 0, email);
        soumissionService.passerQuestionSuivante(soumissionId, 1, email);
        soumissionService.passerQuestionSuivante(soumissionId, 2, email); // quiz terminé, remise jamais reçue
        jdbcTemplate.update("update soumissions set date_debut = ?, debut_question_quiz = ? where id = ?",
                LocalDateTime.now().minusMinutes(3), LocalDateTime.now().minusMinutes(3), soumissionId);

        clotureCopiesService.cloturerCopiesExpirees();

        Soumission copie = copie(soumissionId);
        assertThat(copie.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(copie.getDateRemise()).isEqualTo(copie.getDebutQuestionQuiz());
    }

    @Test
    @DisplayName("Retour après la fin du quiz et la marge : la copie est clôturée à la reprise, sans questions")
    void retourApresLaFinDuQuiz_ClotureALaReprise() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        reculer(soumissionId, DUREE_QUIZ + TempsEpreuveService.MARGE_TOLERANCE_MINUTES * 60 + 5);

        SoumissionDetailResponse reprise = soumissionService.demarrerOuReprendre(evaluationId, email);

        assertThat(reprise.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(reprise.getQuestions()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Temps total affiché : celui qui reste jusqu'à la fin du quiz, pas jusqu'à la fin de la durée standard")
    void tempsTotal_FinDuQuiz() {
        SoumissionDetailResponse copie = soumissionService.demarrerOuReprendre(evaluationId, email);

        assertThat(copie.getTempsRestantSecondes()).isBetween((long) DUREE_QUIZ - 5, (long) DUREE_QUIZ);
    }

    @Test
    @DisplayName("Durée standard plus courte que le quiz : elle reste le plafond")
    void dureeStandardPlusCourte_ResteLePlafond() {
        jdbcTemplate.update("update evaluations set duree_standard = 1 where id = ?", evaluationId);
        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        reculer(soumissionId, 60 + TempsEpreuveService.MARGE_TOLERANCE_MINUTES * 60 + 5);

        clotureCopiesService.cloturerCopiesExpirees();

        Soumission copie = copie(soumissionId);
        assertThat(copie.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(copie.getDateRemise()).isEqualTo(copie.getDateDebut().plusMinutes(1));
    }
}
