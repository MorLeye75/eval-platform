package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AntiTricheServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 10, 19, 10, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock
    private SoumissionRepository soumissionRepository;

    @Mock
    private AlerteAntiTricheRepository alerteAntiTricheRepository;

    @Mock
    private EtudiantRepository etudiantRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private AmenagementTempsRepository amenagementTempsRepository;

    private AntiTricheService antiTricheService;
    private Etudiant etudiant;
    private Soumission soumission;

    @BeforeEach
    void setUp() {
        Clock horlogeFigee = Clock.fixed(MAINTENANT.atZone(ZONE).toInstant(), ZONE);
        antiTricheService = new AntiTricheService(soumissionRepository, alerteAntiTricheRepository,
                new SoumissionAccesService(etudiantRepository, utilisateurRepository, soumissionRepository),
                new TempsEpreuveService(amenagementTempsRepository, horlogeFigee));

        etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-2026-42", null);
        etudiant.setId(1L);
        Evaluation evaluation = new Evaluation();
        evaluation.setId(10L);

        soumission = new Soumission();
        soumission.setId(100L);
        soumission.setEtudiant(etudiant);
        soumission.setEvaluation(evaluation);
        soumission.setStatut(StatutSoumission.EN_COURS);
        soumission.setNbChangementsOnglet(0);
    }

    private void etudiantEtCopieVerrouillee() {
        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(soumission));
    }

    @Test
    @DisplayName("Changement d'onglet : alerte MODERE au 3e changement, datée par l'horloge")
    void alerteModereeAuTroisiemeChangement() {
        soumission.setNbChangementsOnglet(2);
        etudiantEtCopieVerrouillee();

        antiTricheService.enregistrerChangementOnglet(100L, "etudiant@ept.sn");

        assertThat(soumission.getNbChangementsOnglet()).isEqualTo(3);
        verify(soumissionRepository).save(soumission);
        verify(alerteAntiTricheRepository).save(argThat(alerte ->
                alerte.getNiveau() == NiveauAlerte.MODERE
                        && alerte.getDescription().contains("3 changements d'onglets")
                        && alerte.getDateDetection().equals(MAINTENANT)));
    }

    @Test
    @DisplayName("Changement d'onglet : pas de nouvelle alerte entre deux seuils, alerte ELEVE au 5e")
    void alerteUniquementAuxSeuils() {
        soumission.setNbChangementsOnglet(3);
        etudiantEtCopieVerrouillee();

        antiTricheService.enregistrerChangementOnglet(100L, "etudiant@ept.sn"); // 4e : aucune alerte
        verify(alerteAntiTricheRepository, never()).save(any());

        antiTricheService.enregistrerChangementOnglet(100L, "etudiant@ept.sn"); // 5e : alerte ELEVE
        verify(alerteAntiTricheRepository).save(argThat(alerte -> alerte.getNiveau() == NiveauAlerte.ELEVE));
    }

    @Test
    @DisplayName("Changement d'onglet : ignoré une fois la copie soumise")
    void ignoreApresSoumission() {
        soumission.setStatut(StatutSoumission.SOUMIS);
        etudiantEtCopieVerrouillee();

        antiTricheService.enregistrerChangementOnglet(100L, "etudiant@ept.sn");

        assertThat(soumission.getNbChangementsOnglet()).isZero();
        verify(soumissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Changement d'onglet : refusé sur la copie d'un autre étudiant")
    void refuseSurCopieDUnAutreEtudiant() {
        Etudiant autre = new Etudiant("Autre", "Etudiant", "autre@ept.sn", "pass", "ETU-2026-43", null);
        autre.setId(2L);
        when(etudiantRepository.findByEmail("autre@ept.sn")).thenReturn(Optional.of(autre));
        when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(soumission));

        assertThatThrownBy(() -> antiTricheService.enregistrerChangementOnglet(100L, "autre@ept.sn"))
                .isInstanceOf(AccesRefuseException.class);
    }
}
