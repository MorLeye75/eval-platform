package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.evaluation.AmenagementResponse;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.AmenagementTemps;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.AmenagementTempsRepository;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DS-06 : tiers-temps, verrouillé dès que l'étudiant a commencé sa copie. */
@ExtendWith(MockitoExtension.class)
class AmenagementTempsServiceTest {

    @Mock private EvaluationService evaluationService;
    @Mock private EtudiantRepository etudiantRepository;
    @Mock private AmenagementTempsRepository amenagementTempsRepository;
    @Mock private SoumissionRepository soumissionRepository;

    private AmenagementTempsService service;
    private Enseignant prof;
    private Evaluation evaluation;
    private Etudiant etudiant;

    @BeforeEach
    void setUp() {
        service = new AmenagementTempsService(evaluationService, etudiantRepository, amenagementTempsRepository, soumissionRepository);

        prof = new Enseignant("Sow", "Mamadou", "prof@ept.sn", "x", "ENS-1");
        prof.setId(1L);
        Promotion dic2 = new Promotion("DIC2", "2025/2026");
        dic2.setId(5L);

        evaluation = new Evaluation();
        evaluation.setId(10L);
        evaluation.setDureeStandard(60);
        evaluation.setStatut(StatutEvaluation.PUBLIEE);
        evaluation.setEnseignant(prof);
        evaluation.setPromotions(List.of(dic2));

        etudiant = new Etudiant("Niang", "Aminou", "etu@ept.sn", "x", "ETU-1", dic2);
        etudiant.setId(7L);

        lenient().when(evaluationService.enseignantConnecte("prof@ept.sn")).thenReturn(prof);
        lenient().when(evaluationService.evaluationDeLEnseignant(10L, prof)).thenReturn(evaluation);
        lenient().when(etudiantRepository.findById(7L)).thenReturn(Optional.of(etudiant));
    }

    @Test
    @DisplayName("Création d'un aménagement : durée enregistrée pour (étudiant, évaluation)")
    void definir_Creation() {
        when(soumissionRepository.existsByEvaluationIdAndEtudiantId(10L, 7L)).thenReturn(false);
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(7L, 10L)).thenReturn(Optional.empty());

        AmenagementResponse r = service.definir(10L, 7L, 80, "prof@ept.sn");

        ArgumentCaptor<AmenagementTemps> captor = ArgumentCaptor.forClass(AmenagementTemps.class);
        verify(amenagementTempsRepository).save(captor.capture());
        assertThat(captor.getValue().getDureePersonnalisee()).isEqualTo(80);
        assertThat(captor.getValue().getEtudiant()).isSameAs(etudiant);
        assertThat(r.getDureeMinutes()).isEqualTo(80);
        assertThat(r.getEtudiantId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Mise à jour d'un aménagement existant : la ligne existante est modifiée, pas dupliquée")
    void definir_MiseAJour() {
        AmenagementTemps existant = new AmenagementTemps(etudiant, evaluation, 70);
        when(soumissionRepository.existsByEvaluationIdAndEtudiantId(10L, 7L)).thenReturn(false);
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(7L, 10L)).thenReturn(Optional.of(existant));

        service.definir(10L, 7L, 90, "prof@ept.sn");

        verify(amenagementTempsRepository).save(existant);
        assertThat(existant.getDureePersonnalisee()).isEqualTo(90);
    }

    @Test
    @DisplayName("Copie déjà commencée → 409 : le temps d'une copie en cours ne change pas")
    void definir_CopieCommencee() {
        when(soumissionRepository.existsByEvaluationIdAndEtudiantId(10L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.definir(10L, 7L, 90, "prof@ept.sn"))
                .isInstanceOf(EvaluationNonModifiableException.class)
                .hasMessageContaining("déjà commencé");
        verify(amenagementTempsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Durée inférieure à la durée standard → 400")
    void definir_DureeTropCourte() {
        assertThatThrownBy(() -> service.definir(10L, 7L, 59, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("augmenter");
        verify(amenagementTempsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Étudiant d'une promotion non concernée, ou sans promotion → 400")
    void definir_HorsPromotion() {
        Promotion autre = new Promotion("DIC3", "2025/2026");
        autre.setId(6L);
        etudiant.setPromotion(autre);
        assertThatThrownBy(() -> service.definir(10L, 7L, 80, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("promotion");

        etudiant.setPromotion(null);
        assertThatThrownBy(() -> service.definir(10L, 7L, 80, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Évaluation terminée → 409")
    void definir_EvaluationTerminee() {
        evaluation.setStatut(StatutEvaluation.TERMINEE);

        assertThatThrownBy(() -> service.definir(10L, 7L, 80, "prof@ept.sn"))
                .isInstanceOf(EvaluationNonModifiableException.class);
    }

    @Test
    @DisplayName("Étudiant inconnu → 404")
    void definir_EtudiantInconnu() {
        when(etudiantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definir(10L, 99L, 80, "prof@ept.sn"))
                .isInstanceOf(RessourceNonTrouveeException.class);
    }

    @Test
    @DisplayName("Évaluation d'un autre enseignant → 403 (contrôle délégué à EvaluationService)")
    void definir_AutreProprietaire() {
        lenient().when(evaluationService.evaluationDeLEnseignant(11L, prof)).thenThrow(new AccesRefuseException("autre enseignant"));

        assertThatThrownBy(() -> service.definir(11L, 7L, 80, "prof@ept.sn")).isInstanceOf(AccesRefuseException.class);
        verify(amenagementTempsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Suppression : aménagement supprimé tant que la copie n'est pas commencée")
    void supprimer() {
        AmenagementTemps existant = mock(AmenagementTemps.class);
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(7L, 10L)).thenReturn(Optional.of(existant));
        when(soumissionRepository.existsByEvaluationIdAndEtudiantId(10L, 7L)).thenReturn(false);

        service.supprimer(10L, 7L, "prof@ept.sn");

        verify(amenagementTempsRepository).delete(existant);
    }

    @Test
    @DisplayName("Suppression : copie commencée → 409 ; aménagement absent → 404")
    void supprimer_Refus() {
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(7L, 10L))
                .thenReturn(Optional.of(new AmenagementTemps(etudiant, evaluation, 80)));
        when(soumissionRepository.existsByEvaluationIdAndEtudiantId(10L, 7L)).thenReturn(true);
        assertThatThrownBy(() -> service.supprimer(10L, 7L, "prof@ept.sn")).isInstanceOf(EvaluationNonModifiableException.class);
        verify(amenagementTempsRepository, never()).delete(any());

        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(7L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.supprimer(10L, 7L, "prof@ept.sn")).isInstanceOf(RessourceNonTrouveeException.class);
    }
}
