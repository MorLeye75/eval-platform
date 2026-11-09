package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.evaluation.ResultatsPublicationResponse;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DS-07 : publication des résultats par l'enseignant. */
@ExtendWith(MockitoExtension.class)
class PublicationResultatsServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 11, 10, 14, 0);

    @Mock private EvaluationService evaluationService;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private SoumissionRepository soumissionRepository;
    @Mock private NotificationService notificationService;
    @Mock private TempsEpreuveService tempsEpreuveService;

    private PublicationResultatsService service;
    private Enseignant prof;
    private Evaluation evaluation;

    @BeforeEach
    void setUp() {
        service = new PublicationResultatsService(evaluationService, evaluationRepository, soumissionRepository,
                notificationService, tempsEpreuveService);

        prof = new Enseignant("Sow", "Mamadou", "prof@ept.sn", "x", "ENS-1");
        prof.setId(1L);

        evaluation = new Evaluation();
        evaluation.setId(10L);
        evaluation.setStatut(StatutEvaluation.TERMINEE);
        evaluation.setResultatsPublies(false);
        evaluation.setEnseignant(prof);
        evaluation.setDateFermeture(MAINTENANT.minusMinutes(30)); // fermée depuis 30 min : marge de 2 min écoulée

        org.mockito.Mockito.lenient().when(evaluationService.enseignantConnecte("prof@ept.sn")).thenReturn(prof);
        org.mockito.Mockito.lenient().when(evaluationService.evaluationDeLEnseignant(10L, prof)).thenReturn(evaluation);
        org.mockito.Mockito.lenient().when(tempsEpreuveService.maintenant()).thenReturn(MAINTENANT);
    }

    @Test
    @DisplayName("Publication : bascule atomique, notification des copies notées, bilan avec les copies en attente")
    void publier() {
        when(evaluationRepository.publierResultatsSiNonPublies(10L)).thenReturn(1);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation));
        when(soumissionRepository.countByEvaluationId(10L)).thenReturn(5L);
        when(soumissionRepository.countByEvaluationIdAndStatut(10L, StatutSoumission.EVALUE)).thenReturn(4L);

        ResultatsPublicationResponse r = service.publier(10L, "prof@ept.sn");

        assertThat(r.getResultatsPublies()).isTrue();
        assertThat(r.getNbCopies()).isEqualTo(5);
        assertThat(r.getNbCopiesNotees()).isEqualTo(4);
        assertThat(r.getNbCopiesEnAttente()).isEqualTo(1);
        // La notification vient après la bascule, dans la même transaction
        InOrder ordre = inOrder(evaluationRepository, notificationService);
        ordre.verify(evaluationRepository).publierResultatsSiNonPublies(10L);
        ordre.verify(notificationService).resultatsPublies(evaluation);
    }

    @Test
    @DisplayName("Résultats déjà publiés → 409, aucune notification en double")
    void dejaPublies() {
        evaluation.setResultatsPublies(true);

        assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn"))
                .isInstanceOf(EvaluationNonModifiableException.class)
                .hasMessageContaining("déjà publiés");
        verify(evaluationRepository, never()).publierResultatsSiNonPublies(10L);
        verify(notificationService, never()).resultatsPublies(any());
    }

    @Test
    @DisplayName("Deux clics simultanés : la bascule conditionnelle renvoie 0 pour le second → 409, pas de notification")
    void doubleClic() {
        when(evaluationRepository.publierResultatsSiNonPublies(10L)).thenReturn(0);

        assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn"))
                .isInstanceOf(EvaluationNonModifiableException.class)
                .hasMessageContaining("déjà publiés");
        verify(notificationService, never()).resultatsPublies(any());
    }

    @Test
    @DisplayName("Évaluation pas terminée (brouillon, publiée, en cours) → 409 : aucun corrigé tant que des étudiants composent")
    void pasTerminee() {
        for (StatutEvaluation statut : new StatutEvaluation[]{StatutEvaluation.BROUILLON, StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS}) {
            evaluation.setStatut(statut);

            assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn"))
                    .isInstanceOf(EvaluationNonModifiableException.class)
                    .hasMessageContaining("terminée");
        }
        verify(evaluationRepository, never()).publierResultatsSiNonPublies(10L);
    }

    @Test
    @DisplayName("Moins de 2 minutes après la fermeture : marge de tolérance, un étudiant peut encore rendre sa copie → 409")
    void margeDeTolerance() {
        evaluation.setDateFermeture(MAINTENANT.minusMinutes(1));

        assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn"))
                .isInstanceOf(EvaluationNonModifiableException.class)
                .hasMessageContaining("marge de tolérance");
        verify(notificationService, never()).resultatsPublies(any());
    }

    @Test
    @DisplayName("Exactement à la fin de la marge (fermeture + 2 min) : publication acceptée")
    void finDeLaMarge() {
        evaluation.setDateFermeture(MAINTENANT.minusMinutes(TempsEpreuveService.MARGE_TOLERANCE_MINUTES));
        when(evaluationRepository.publierResultatsSiNonPublies(10L)).thenReturn(1);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation));

        assertThat(service.publier(10L, "prof@ept.sn").getResultatsPublies()).isTrue();
    }

    @Test
    @DisplayName("Évaluation d'un autre enseignant → 403 ; inconnue → 404")
    void acces() {
        when(evaluationService.evaluationDeLEnseignant(11L, prof)).thenThrow(new AccesRefuseException("autre enseignant"));
        when(evaluationService.evaluationDeLEnseignant(99L, prof)).thenThrow(new RessourceNonTrouveeException("inconnue"));

        assertThatThrownBy(() -> service.publier(11L, "prof@ept.sn")).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.publier(99L, "prof@ept.sn")).isInstanceOf(RessourceNonTrouveeException.class);
        verify(evaluationRepository, never()).publierResultatsSiNonPublies(any());
    }
}
