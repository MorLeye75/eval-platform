package sn.ept.eval.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Clôture automatique (DS-11) : seules les copies encore EN_COURS sous verrou sont clôturées. */
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

    @Mock
    private EntityManager entityManager;

    private ClotureCopiesService service;
    private Soumission soumission;

    @BeforeEach
    void setUp() {
        Clock horlogeFigee = Clock.fixed(MAINTENANT.atZone(ZONE).toInstant(), ZONE);
        TempsEpreuveService temps = new TempsEpreuveService(amenagementTempsRepository, horlogeFigee);
        service = new ClotureCopiesService(soumissionRepository, temps, new CorrectionDeclencheur(correctionService), entityManager);

        Etudiant etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-2026-42", null);
        etudiant.setId(1L);
        Evaluation evaluation = new Evaluation();
        evaluation.setId(10L);
        evaluation.setDureeStandard(30);
        evaluation.setDateFermeture(MAINTENANT.plusDays(1));

        soumission = new Soumission();
        soumission.setId(100L);
        soumission.setEtudiant(etudiant);
        soumission.setEvaluation(evaluation);
        soumission.setStatut(StatutSoumission.EN_COURS);
    }

    @Test
    @DisplayName("Copie expirée : relue sous verrou, clôturée à sa fin théorique, correction lancée")
    void copieExpiree_Cloturee() {
        soumission.setDateDebut(MAINTENANT.minusMinutes(40)); // 30 min + marge de 2 min dépassées
        when(soumissionRepository.findByStatut(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));

        assertThat(service.cloturerCopiesExpirees()).isEqualTo(1);

        verify(entityManager).refresh(soumission, LockModeType.PESSIMISTIC_WRITE);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(soumission.getDateRemise()).isEqualTo(MAINTENANT.minusMinutes(10));
        verify(correctionService).corrigerSoumissionAsync(100L);
    }

    @Test
    @DisplayName("Copie remise entre la sélection et le verrou (soumission ou reprise) : ni clôture ni correction")
    void copieRemiseEntreTemps_Ignoree() {
        soumission.setDateDebut(MAINTENANT.minusMinutes(40));
        when(soumissionRepository.findByStatut(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));
        // La relecture sous verrou voit l'état validé par la transaction concurrente
        doAnswer(invocation -> {
            soumission.setStatut(StatutSoumission.SOUMIS);
            soumission.setDateRemise(MAINTENANT.minusMinutes(12));
            return null;
        }).when(entityManager).refresh(soumission, LockModeType.PESSIMISTIC_WRITE);

        assertThat(service.cloturerCopiesExpirees()).isZero();

        assertThat(soumission.getDateRemise()).isEqualTo(MAINTENANT.minusMinutes(12)); // date de la vraie remise conservée
        verify(correctionService, never()).corrigerSoumissionAsync(anyLong());
    }

    @Test
    @DisplayName("Copie dans les temps : ni verrou ni clôture")
    void copieDansLesTemps_Intacte() {
        soumission.setDateDebut(MAINTENANT.minusMinutes(10));
        when(soumissionRepository.findByStatut(StatutSoumission.EN_COURS)).thenReturn(List.of(soumission));

        assertThat(service.cloturerCopiesExpirees()).isZero();

        verify(entityManager, never()).refresh(any(), any(LockModeType.class));
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_COURS);
    }
}
