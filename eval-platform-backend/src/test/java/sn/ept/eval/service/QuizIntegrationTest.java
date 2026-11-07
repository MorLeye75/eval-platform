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
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
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
}
