package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.etudiant.EvaluationEtudiantResponse;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.TypeQuestion;
import sn.ept.eval.repository.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationEtudiantServiceTest {

    @Mock
    private EtudiantRepository etudiantRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private SoumissionRepository soumissionRepository;

    @Mock
    private AmenagementTempsRepository amenagementTempsRepository;

    private EvaluationEtudiantService service;
    private Etudiant etudiant;
    private Promotion dic2;

    @BeforeEach
    void setUp() {
        SoumissionAccesService accesService = new SoumissionAccesService(etudiantRepository, utilisateurRepository, soumissionRepository);
        service = new EvaluationEtudiantService(accesService, evaluationRepository, soumissionRepository, amenagementTempsRepository);

        dic2 = new Promotion("DIC2 Informatique", "2025/2026");
        dic2.setId(5L);
        etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-2026-42", dic2);
        etudiant.setId(1L);
        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
    }

    @Test
    @DisplayName("Étudiant sans promotion : liste vide, aucune évaluation chargée")
    void sansPromotion_ListeVide() {
        etudiant.setPromotion(null);

        assertThat(service.mesEvaluations("etudiant@ept.sn")).isEmpty();
        verify(evaluationRepository, never()).findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(anyLong(), any());
    }

    @Test
    @DisplayName("Les brouillons ne sont jamais demandés : seuls PUBLIEE, EN_COURS et TERMINEE sont visibles")
    @SuppressWarnings("unchecked")
    void brouillonsExclus() {
        when(evaluationRepository.findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(eq(5L), any())).thenReturn(List.of());

        service.mesEvaluations("etudiant@ept.sn");

        ArgumentCaptor<Collection<StatutEvaluation>> statuts = ArgumentCaptor.forClass(Collection.class);
        verify(evaluationRepository).findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(eq(5L), statuts.capture());
        assertThat(statuts.getValue()).containsExactlyInAnyOrder(
                StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS, StatutEvaluation.TERMINEE);
    }

    @Test
    @DisplayName("Durée effective (tiers-temps), types de questions, durée du quiz et état de la copie")
    void evaluationAvecTiersTempsEtCopie() {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(10L);
        evaluation.setTitre("Quiz Réseaux");
        evaluation.setDureeStandard(30);
        evaluation.setStatut(StatutEvaluation.EN_COURS);
        evaluation.addQuestion(new QuestionQCM("Couche du protocole IP ?", 1, 2.0, false));
        evaluation.addQuestion(new QuestionQuiz("Port HTTPS ?", 2, 1.0, false, 20));

        Soumission copie = new Soumission();
        copie.setId(100L);
        copie.setEvaluation(evaluation);
        copie.setStatut(StatutSoumission.SOUMIS);

        when(evaluationRepository.findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(eq(5L), any())).thenReturn(List.of(evaluation));
        when(soumissionRepository.findByEtudiantId(1L)).thenReturn(List.of(copie));
        when(amenagementTempsRepository.findByEtudiantId(1L)).thenReturn(List.of(new AmenagementTemps(etudiant, evaluation, 40)));

        EvaluationEtudiantResponse r = service.mesEvaluations("etudiant@ept.sn").get(0);

        assertThat(r.getDureeMinutes()).isEqualTo(40); // tiers-temps, et non la durée standard de 30 min
        assertThat(r.getNbQuestions()).isEqualTo(2);
        assertThat(r.getTypesQuestions()).containsExactly(TypeQuestion.QCM, TypeQuestion.QUIZ_CHRONOMETREE);
        assertThat(r.getDureeQuestion()).isEqualTo(20);
        assertThat(r.getSoumissionId()).isEqualTo(100L);
        assertThat(r.getStatutSoumission()).isEqualTo(StatutSoumission.SOUMIS);
    }

    @Test
    @DisplayName("Évaluation sans copie ni aménagement : durée standard, pas de copie, pas de durée par question")
    void evaluationSansCopie() {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(11L);
        evaluation.setDureeStandard(45);
        evaluation.addQuestion(new QuestionOuverte());

        when(evaluationRepository.findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(eq(5L), any())).thenReturn(List.of(evaluation));

        EvaluationEtudiantResponse r = service.mesEvaluations("etudiant@ept.sn").get(0);

        assertThat(r.getDureeMinutes()).isEqualTo(45);
        assertThat(r.getSoumissionId()).isNull();
        assertThat(r.getStatutSoumission()).isNull();
        assertThat(r.getDureeQuestion()).isNull();
    }
}
