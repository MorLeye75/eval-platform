package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.etudiant.EvaluationEtudiantResponse;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.AmenagementTemps;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.QuestionQuiz;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.TypeQuestion;
import sn.ept.eval.repository.AmenagementTempsRepository;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Détail d'une évaluation côté étudiant (GET /etudiant/evaluations/{id}), demandé pour l'écran d'accueil du quiz. */
@ExtendWith(MockitoExtension.class)
class EvaluationEtudiantDetailTest {

    @Mock private EtudiantRepository etudiantRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private SoumissionRepository soumissionRepository;
    @Mock private AmenagementTempsRepository amenagementTempsRepository;

    private EvaluationEtudiantService service;
    private Etudiant etudiant;
    private Promotion dic2;

    @BeforeEach
    void setUp() {
        SoumissionAccesService acces = new SoumissionAccesService(etudiantRepository, utilisateurRepository, soumissionRepository);
        service = new EvaluationEtudiantService(acces, evaluationRepository, soumissionRepository, amenagementTempsRepository);

        dic2 = new Promotion("DIC2", "2025/2026");
        dic2.setId(5L);
        etudiant = new Etudiant("Niang", "Aminou", "etu@ept.sn", "x", "ETU-1", dic2);
        etudiant.setId(1L);
        lenient().when(etudiantRepository.findByEmail("etu@ept.sn")).thenReturn(Optional.of(etudiant));
    }

    @Test
    @DisplayName("Détail d'un quiz : durée par question, durée effective (tiers-temps) et état de la copie")
    void detailQuiz() {
        Evaluation quiz = evaluation(StatutEvaluation.EN_COURS);
        quiz.addQuestion(new QuestionQuiz("Port HTTPS ?", 1, 1.0, false, 20));
        Soumission copie = new Soumission();
        copie.setId(100L);
        copie.setStatut(StatutSoumission.EN_COURS);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(soumissionRepository.findByEvaluationIdAndEtudiantId(10L, 1L)).thenReturn(Optional.of(copie));
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L))
                .thenReturn(Optional.of(new AmenagementTemps(etudiant, quiz, 40)));

        EvaluationEtudiantResponse r = service.detailEvaluation(10L, "etu@ept.sn");

        assertThat(r.getId()).isEqualTo(10L);
        assertThat(r.getDureeMinutes()).isEqualTo(40);
        assertThat(r.getDureeQuestion()).isEqualTo(20);
        assertThat(r.getTypesQuestions()).containsExactly(TypeQuestion.QUIZ_CHRONOMETREE);
        assertThat(r.getSoumissionId()).isEqualTo(100L);
        assertThat(r.getStatutSoumission()).isEqualTo(StatutSoumission.EN_COURS);
    }

    @Test
    @DisplayName("Sans copie ni aménagement : durée standard, pas de copie")
    void detailSansCopie() {
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.PUBLIEE)));
        when(soumissionRepository.findByEvaluationIdAndEtudiantId(10L, 1L)).thenReturn(Optional.empty());
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L)).thenReturn(Optional.empty());

        EvaluationEtudiantResponse r = service.detailEvaluation(10L, "etu@ept.sn");

        assertThat(r.getDureeMinutes()).isEqualTo(30);
        assertThat(r.getSoumissionId()).isNull();
    }

    @Test
    @DisplayName("Un brouillon est invisible (404) : on ne révèle pas son existence")
    void brouillonIntrouvable() {
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.BROUILLON)));

        assertThatThrownBy(() -> service.detailEvaluation(10L, "etu@ept.sn")).isInstanceOf(RessourceNonTrouveeException.class);
    }

    @Test
    @DisplayName("Évaluation inconnue → 404")
    void inconnue() {
        when(evaluationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detailEvaluation(99L, "etu@ept.sn")).isInstanceOf(RessourceNonTrouveeException.class);
    }

    @Test
    @DisplayName("Évaluation d'une autre promotion → 403")
    void autrePromotion() {
        Promotion dic3 = new Promotion("DIC3", "2025/2026");
        dic3.setId(6L);
        Evaluation autre = evaluation(StatutEvaluation.EN_COURS);
        autre.setPromotions(Set.of(dic3));
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(autre));

        assertThatThrownBy(() -> service.detailEvaluation(10L, "etu@ept.sn")).isInstanceOf(AccesRefuseException.class);
    }

    @Test
    @DisplayName("Étudiant sans promotion → 403")
    void sansPromotion() {
        etudiant.setPromotion(null);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.EN_COURS)));

        assertThatThrownBy(() -> service.detailEvaluation(10L, "etu@ept.sn")).isInstanceOf(AccesRefuseException.class);
    }

    private Evaluation evaluation(StatutEvaluation statut) {
        Evaluation e = new Evaluation();
        e.setId(10L);
        e.setTitre("Quiz Réseaux");
        e.setDureeStandard(30);
        e.setStatut(statut);
        e.setPromotions(Set.of(dic2));
        return e;
    }
}
