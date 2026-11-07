package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import sn.ept.eval.model.AmenagementTemps;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.AmenagementTempsRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClotureCopiesServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 10, 19, 10, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock
    private SoumissionRepository soumissionRepository;

    @Mock
    private AmenagementTempsRepository amenagementTempsRepository;

    @Mock
    private CorrectionService correctionService;

    /** Gestionnaire simulé : chaque clôture s'exécute « dans sa transaction » sans base. */
    @Mock
    private PlatformTransactionManager transactionManager;

    private ClotureCopiesService service;
    private Etudiant etudiant;
    private Evaluation evaluation;
    private Soumission soumission;

    @BeforeEach
    void setUp() {
        Clock horlogeFigee = Clock.fixed(MAINTENANT.atZone(ZONE).toInstant(), ZONE);
        TempsEpreuveService temps = new TempsEpreuveService(amenagementTempsRepository, horlogeFigee);
        service = new ClotureCopiesService(soumissionRepository, temps, new CorrectionDeclencheur(correctionService),
                transactionManager);

        etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-2026-42", null);
        etudiant.setId(1L);
        evaluation = new Evaluation();
        evaluation.setId(10L);
        evaluation.setDureeStandard(30);
        evaluation.setDateFermeture(MAINTENANT.plusDays(1));

        soumission = copie(100L, MAINTENANT.minusMinutes(40)); // 30 min + marge de 2 min dépassées
    }

    private Soumission copie(Long id, LocalDateTime debut) {
        Soumission s = new Soumission();
        s.setId(id);
        s.setEtudiant(etudiant);
        s.setEvaluation(evaluation);
        s.setStatut(StatutSoumission.EN_COURS);
        s.setDateDebut(debut);
        return s;
    }

    @Test
    @DisplayName("Copie expirée : relue sous verrou, clôturée à sa fin théorique, correction lancée")
    void copieExpiree_Cloturee() {
        when(soumissionRepository.findByStatutAvecEvaluation(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));
        when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(soumission));

        assertThat(service.cloturerCopiesExpirees()).isEqualTo(1);

        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(soumission.getDateRemise()).isEqualTo(MAINTENANT.minusMinutes(10));
        verify(correctionService).corrigerSoumissionAsync(100L);
    }

    @Test
    @DisplayName("Copie remise entre la sélection et le verrou (soumission ou reprise) : ni clôture ni correction")
    void copieRemiseEntreTemps_Ignoree() {
        when(soumissionRepository.findByStatutAvecEvaluation(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));
        // La relecture sous verrou voit l'état validé par la transaction concurrente
        Soumission relue = copie(100L, MAINTENANT.minusMinutes(40));
        relue.setStatut(StatutSoumission.SOUMIS);
        relue.setDateRemise(MAINTENANT.minusMinutes(12));
        when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(relue));

        assertThat(service.cloturerCopiesExpirees()).isZero();

        assertThat(relue.getDateRemise()).isEqualTo(MAINTENANT.minusMinutes(12)); // date de la vraie remise conservée
        verify(correctionService, never()).corrigerSoumissionAsync(anyLong());
    }

    @Test
    @DisplayName("Aménagement accordé entre la sélection et le verrou : la copie, relue avec sa nouvelle durée, reste ouverte")
    void amenagementAccordeEntreTemps_CopieIntacte() {
        when(soumissionRepository.findByStatutAvecEvaluation(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));
        when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(soumission));
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L))
                .thenReturn(Optional.of(new AmenagementTemps(etudiant, evaluation, 90)));

        assertThat(service.cloturerCopiesExpirees()).isZero();

        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_COURS);
        verify(correctionService, never()).corrigerSoumissionAsync(anyLong());
    }

    @Test
    @DisplayName("Aménagement connu à la sélection (tiers-temps) : la copie n'est même pas verrouillée")
    void amenagementEnLot_CopieNonVerrouillee() {
        when(soumissionRepository.findByStatutAvecEvaluation(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));
        when(amenagementTempsRepository.findByEvaluationIdIn(any()))
                .thenReturn(List.of(new AmenagementTemps(etudiant, evaluation, 90)));

        assertThat(service.cloturerCopiesExpirees()).isZero();

        verify(soumissionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Une copie qui ne peut pas être verrouillée n'empêche pas la clôture des autres")
    void echecSurUneCopie_LesAutresCloturees() {
        Soumission autre = copie(101L, MAINTENANT.minusMinutes(40));
        when(soumissionRepository.findByStatutAvecEvaluation(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission, autre));
        when(soumissionRepository.findByIdForUpdate(100L)).thenThrow(new PessimisticLockingFailureException("verrou détenu"));
        when(soumissionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(autre));

        assertThat(service.cloturerCopiesExpirees()).isEqualTo(1);

        assertThat(autre.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_COURS);
    }

    @Test
    @DisplayName("Copie dans les temps : ni verrou ni clôture")
    void copieDansLesTemps_Intacte() {
        soumission.setDateDebut(MAINTENANT.minusMinutes(10));
        when(soumissionRepository.findByStatutAvecEvaluation(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));

        assertThat(service.cloturerCopiesExpirees()).isZero();

        verify(soumissionRepository, never()).findByIdForUpdate(any());
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_COURS);
    }
}
