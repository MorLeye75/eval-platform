package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DS-05 : passage automatique des statuts, avec une horloge figée. */
@ExtendWith(MockitoExtension.class)
class EvaluationStatutServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 10, 26, 10, 0);

    @Mock private EvaluationRepository evaluationRepository;

    private EvaluationStatutService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(MAINTENANT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new EvaluationStatutService(evaluationRepository, clock);
        // Par défaut : rien à faire
        when(evaluationRepository.findByStatutAndDateFermetureLessThanEqual(any(), any())).thenReturn(List.of());
        when(evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("PUBLIEE dont l'ouverture est atteinte → EN_COURS")
    void ouverture() {
        Evaluation aOuvrir = evaluation(StatutEvaluation.PUBLIEE);
        when(evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT))
                .thenReturn(List.of(aOuvrir));

        int modifiees = service.mettreAJourStatuts();

        assertThat(aOuvrir.getStatut()).isEqualTo(StatutEvaluation.EN_COURS);
        assertThat(modifiees).isEqualTo(1);
    }

    @Test
    @DisplayName("PUBLIEE dont la fermeture est déjà passée → TERMINEE directement, sans passer par EN_COURS")
    void publieeDejaFermee() {
        Evaluation fermee = evaluation(StatutEvaluation.PUBLIEE);
        when(evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT))
                .thenReturn(List.of(fermee));

        int modifiees = service.mettreAJourStatuts();

        assertThat(fermee.getStatut()).isEqualTo(StatutEvaluation.TERMINEE);
        assertThat(modifiees).isEqualTo(1);
        // Les fermetures sont traitées avant les ouvertures : l'évaluation n'est jamais passée en EN_COURS
        org.mockito.InOrder ordre = org.mockito.Mockito.inOrder(evaluationRepository);
        ordre.verify(evaluationRepository).findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT);
        ordre.verify(evaluationRepository).findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT);
    }

    @Test
    @DisplayName("EN_COURS dont la fermeture est atteinte → TERMINEE")
    void fermeture() {
        Evaluation aFermer = evaluation(StatutEvaluation.EN_COURS);
        when(evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.EN_COURS, MAINTENANT))
                .thenReturn(List.of(aFermer));

        assertThat(service.mettreAJourStatuts()).isEqualTo(1);
        assertThat(aFermer.getStatut()).isEqualTo(StatutEvaluation.TERMINEE);
    }

    @Test
    @DisplayName("Rien à faire : aucune évaluation modifiée, retour 0")
    void rienAFaire() {
        assertThat(service.mettreAJourStatuts()).isZero();
    }

    @Test
    @DisplayName("Le brouillon n'est jamais touché : seules les évaluations PUBLIEE / EN_COURS sont interrogées")
    void brouillonsIgnores() {
        service.mettreAJourStatuts();

        verify(evaluationRepository, never()).findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.BROUILLON, MAINTENANT);
        verify(evaluationRepository, never()).findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.BROUILLON, MAINTENANT);
        verify(evaluationRepository, never()).findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.TERMINEE, MAINTENANT);
    }

    private Evaluation evaluation(StatutEvaluation statut) {
        Evaluation e = new Evaluation();
        e.setId(1L);
        e.setStatut(statut);
        e.setDateOuverture(MAINTENANT.minusHours(1));
        e.setDateFermeture(MAINTENANT.minusMinutes(1));
        return e;
    }
}
