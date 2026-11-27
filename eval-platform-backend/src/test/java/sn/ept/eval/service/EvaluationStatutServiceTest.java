package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DS-05 : passage automatique des statuts par mises à jour conditionnelles, avec une horloge figée. */
@ExtendWith(MockitoExtension.class)
class EvaluationStatutServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 10, 26, 10, 0);

    @Mock private EvaluationRepository evaluationRepository;
    @Mock private NotificationService notificationService;
    @Mock private PlatformTransactionManager transactionManager;

    private EvaluationStatutService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(MAINTENANT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new EvaluationStatutService(evaluationRepository, notificationService, clock, transactionManager);
        // Par défaut : rien à faire
        lenient().when(evaluationRepository.findByStatutAndDateFermetureLessThanEqual(any(), any())).thenReturn(List.of());
        lenient().when(evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("PUBLIEE dont l'ouverture est atteinte → EN_COURS, et une seule notification « évaluation ouverte »")
    void ouverture() {
        Evaluation aOuvrir = evaluation(1L);
        when(evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT))
                .thenReturn(List.of(aOuvrir));
        when(evaluationRepository.changerStatutSi(1L, StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS)).thenReturn(1);
        when(evaluationRepository.findById(1L)).thenReturn(Optional.of(aOuvrir));

        int modifiees = service.mettreAJourStatuts();

        assertThat(modifiees).isEqualTo(1);
        verify(notificationService).evaluationOuverte(aOuvrir);
    }

    @Test
    @DisplayName("Transition déjà faite ailleurs (0 ligne modifiée) : aucune notification, pas comptée")
    void transitionDejaFaite_PasDeDoublon() {
        Evaluation deja = evaluation(1L);
        when(evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT))
                .thenReturn(List.of(deja));
        when(evaluationRepository.changerStatutSi(1L, StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS)).thenReturn(0);

        assertThat(service.mettreAJourStatuts()).isZero();

        verify(notificationService, never()).evaluationOuverte(any());
        verify(evaluationRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("PUBLIEE dont la fermeture est déjà passée → TERMINEE directement, sans notification « ouverte »")
    void publieeDejaFermee() {
        Evaluation fermee = evaluation(1L);
        when(evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT))
                .thenReturn(List.of(fermee));
        when(evaluationRepository.changerStatutSi(1L, StatutEvaluation.PUBLIEE, StatutEvaluation.TERMINEE)).thenReturn(1);

        assertThat(service.mettreAJourStatuts()).isEqualTo(1);

        verify(evaluationRepository, never()).changerStatutSi(1L, StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS);
        verify(notificationService, never()).evaluationOuverte(any());
        // Les fermetures sont traitées avant les ouvertures
        InOrder ordre = inOrder(evaluationRepository);
        ordre.verify(evaluationRepository).findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT);
        ordre.verify(evaluationRepository).findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT);
    }

    @Test
    @DisplayName("EN_COURS dont la fermeture est atteinte → TERMINEE")
    void fermeture() {
        Evaluation aFermer = evaluation(2L);
        when(evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.EN_COURS, MAINTENANT))
                .thenReturn(List.of(aFermer));
        when(evaluationRepository.changerStatutSi(2L, StatutEvaluation.EN_COURS, StatutEvaluation.TERMINEE)).thenReturn(1);

        assertThat(service.mettreAJourStatuts()).isEqualTo(1);
        verify(notificationService, never()).evaluationOuverte(any());
    }

    @Test
    @DisplayName("Rien à faire : retour 0, aucune mise à jour")
    void rienAFaire() {
        assertThat(service.mettreAJourStatuts()).isZero();

        verify(evaluationRepository, never()).changerStatutSi(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Une évaluation en échec n'empêche pas les suivantes : l'erreur est isolée et journalisée")
    void echecIsole() {
        Evaluation enEchec = evaluation(1L);
        Evaluation saine = evaluation(2L);
        when(evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, MAINTENANT))
                .thenReturn(List.of(enEchec, saine));
        when(evaluationRepository.changerStatutSi(1L, StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS)).thenReturn(1);
        when(evaluationRepository.changerStatutSi(2L, StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS)).thenReturn(1);
        when(evaluationRepository.findById(1L)).thenReturn(Optional.of(enEchec));
        when(evaluationRepository.findById(2L)).thenReturn(Optional.of(saine));
        org.mockito.Mockito.doThrow(new IllegalStateException("notification impossible"))
                .when(notificationService).evaluationOuverte(enEchec);

        int modifiees = service.mettreAJourStatuts();

        assertThat(modifiees).isEqualTo(1); // seule la seconde est comptée
        verify(notificationService).evaluationOuverte(saine);
    }

    @Test
    @DisplayName("Un brouillon ou une évaluation terminée n'est jamais interrogée")
    void autresStatutsIgnores() {
        service.mettreAJourStatuts();

        verify(evaluationRepository, never()).findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.BROUILLON, MAINTENANT);
        verify(evaluationRepository, never()).findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.BROUILLON, MAINTENANT);
        verify(evaluationRepository, never()).findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.TERMINEE, MAINTENANT);
    }

    private Evaluation evaluation(Long id) {
        Evaluation e = new Evaluation();
        e.setId(id);
        e.setStatut(StatutEvaluation.PUBLIEE);
        e.setDateOuverture(MAINTENANT.minusHours(1));
        e.setDateFermeture(MAINTENANT.minusMinutes(1));
        return e;
    }
}
