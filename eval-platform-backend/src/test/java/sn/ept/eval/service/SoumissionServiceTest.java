package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.dto.soumission.SauvegardeResponse;
import sn.ept.eval.dto.soumission.SoumissionResponse;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.TempsEcouleException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoumissionServiceTest {

    @Mock
    private SoumissionRepository soumissionRepository;

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private EtudiantRepository etudiantRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ChoixReponseRepository choixReponseRepository;

    @Mock
    private ReponseEtudiantRepository reponseEtudiantRepository;

    @Mock
    private AmenagementTempsRepository amenagementTempsRepository;

    @Mock
    private AlerteAntiTricheRepository alerteAntiTricheRepository;

    @Mock
    private CorrectionService correctionService;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    /** Instant de référence figé : les tests ne dépendent plus de l'heure d'exécution. */
    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 10, 19, 10, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private SoumissionService soumissionService;

    private Etudiant etudiant;
    private Evaluation evaluation;
    private Soumission soumission;

    @BeforeEach
    void setUp() {
        Clock horlogeFigee = Clock.fixed(MAINTENANT.atZone(ZONE).toInstant(), ZONE);
        // Collaborateurs réels (règles de temps) branchés sur les dépôts simulés et une horloge figée
        TempsEpreuveService tempsEpreuveService = new TempsEpreuveService(amenagementTempsRepository, horlogeFigee);
        SoumissionMapper soumissionMapper = new SoumissionMapper(questionRepository, reponseEtudiantRepository, tempsEpreuveService);
        soumissionService = new SoumissionService(soumissionRepository, evaluationRepository, etudiantRepository,
                questionRepository, choixReponseRepository, reponseEtudiantRepository,
                alerteAntiTricheRepository, correctionService, utilisateurRepository, tempsEpreuveService, soumissionMapper);

        etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-2026-42", null);
        etudiant.setId(1L);

        evaluation = new Evaluation();
        evaluation.setId(10L);
        evaluation.setTitre("Examen Algorithmique");
        evaluation.setDureeStandard(30); // 30 minutes

        soumission = new Soumission();
        soumission.setId(100L);
        soumission.setEtudiant(etudiant);
        soumission.setEvaluation(evaluation);
        soumission.setDateDebut(MAINTENANT.minusMinutes(10)); // débuté il y a 10 min
        soumission.setStatut(StatutSoumission.EN_COURS);
        soumission.setNbChangementsOnglet(0);
    }

    @Test
    @DisplayName("DS-02 (Cas Normal) : Sauvegarde automatique réussie dans les temps")
    void sauvegarderReponses_DansLesTemps_Succes() {
        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findById(100L)).thenReturn(Optional.of(soumission));
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L)).thenReturn(Optional.empty());

        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(1L, "Réponse ouverte", null, null)
        ));

        SauvegardeResponse response = soumissionService.sauvegarderReponses(100L, request, "etudiant@ept.sn");

        assertThat(response).isNotNull();
        assertThat(response.getDateSauvegarde()).isNotNull();
        assertThat(response.getMessage()).contains("succès");
    }

    @Test
    @DisplayName("DS-02 (Cas d'erreur) : Rejet avec HTTP 409 (TempsEcouleException) si le temps imparti est expiré")
    void sauvegarderReponses_TempsExpire_LèveTempsEcouleException() {
        // Débuté il y a 40 minutes pour une épreuve de 30 minutes (+ 2 min de marge = 32 min max)
        soumission.setDateDebut(MAINTENANT.minusMinutes(40));

        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findById(100L)).thenReturn(Optional.of(soumission));
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L)).thenReturn(Optional.empty());

        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest();

        assertThatThrownBy(() -> soumissionService.sauvegarderReponses(100L, request, "etudiant@ept.sn"))
                .isInstanceOf(TempsEcouleException.class)
                .hasMessageContaining("temps imparti expiré");
    }

    @Test
    @DisplayName("DS-01 (Cas Normal) : Soumission réussie dans les clous -> Statut SOUMIS et déclenchement de CorrectionService")
    void soumettreCopie_DansLesTemps_PasseASoumisEtDeclencheCorrectionAsync() {
        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findById(100L)).thenReturn(Optional.of(soumission));
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L)).thenReturn(Optional.empty());
        when(soumissionRepository.save(any(Soumission.class))).thenAnswer(i -> i.getArgument(0));

        SoumissionResponse response = soumissionService.soumettreCopie(100L, "etudiant@ept.sn");

        assertThat(response.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(soumission.getDateRemise()).isNotNull();

        // Vérifie que CorrectionService est bien déclenché en tâche de fond (@Async)
        verify(correctionService, times(1)).corrigerSoumissionAsync(100L);
    }

    @Test
    @DisplayName("DS-01 (Cas critique) : Soumission hors délai -> Verrouillage copie, alerte anti-triche et HTTP 409")
    void soumettreCopie_TempsDepasseCritique_VerrouilleEtDeclencheAlerte() {
        // Débuté il y a 45 min pour une épreuve de 30 min (+ 2 min marge = 32 min max)
        soumission.setDateDebut(MAINTENANT.minusMinutes(45));

        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findById(100L)).thenReturn(Optional.of(soumission));
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> soumissionService.soumettreCopie(100L, "etudiant@ept.sn"))
                .isInstanceOf(TempsEcouleException.class)
                .hasMessageContaining("Temps dépassé au-delà de la marge de tolérance");

        // Vérifie que la copie est bien figée et verrouillée au statut SOUMIS
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        verify(soumissionRepository).save(soumission);

        // Vérifie qu'une alerte anti-triche de niveau ELEVE a été journalisée
        verify(alerteAntiTricheRepository).save(argThat(alerte ->
                alerte.getNiveau() == NiveauAlerte.ELEVE &&
                alerte.getDescription().contains("Soumission tardive")
        ));

        // La copie figée à son dernier auto-save est tout de même corrigée
        verify(correctionService).corrigerSoumissionAsync(100L);
    }

    @Test
    @DisplayName("Anti-Triche : Détection changement d'onglet et création d'alerte dès 3 sorties")
    void enregistrerChangementOnglet_CreeAlerteDesTroisSorties() {
        soumission.setNbChangementsOnglet(2);

        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(soumission));

        soumissionService.enregistrerChangementOnglet(100L, "etudiant@ept.sn");

        assertThat(soumission.getNbChangementsOnglet()).isEqualTo(3);
        verify(soumissionRepository).save(soumission);
        verify(alerteAntiTricheRepository).save(argThat(alerte ->
                alerte.getNiveau() == NiveauAlerte.MODERE &&
                alerte.getDescription().contains("3 changements d'onglets")
        ));
    }

    @Test
    @DisplayName("Anti-Triche : pas de nouvelle alerte entre deux seuils, alerte ELEVE au 5e changement")
    void enregistrerChangementOnglet_AlerteUniquementAuxSeuils() {
        soumission.setNbChangementsOnglet(3);
        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(soumission));

        soumissionService.enregistrerChangementOnglet(100L, "etudiant@ept.sn"); // 4e : aucune alerte
        verify(alerteAntiTricheRepository, never()).save(any());

        soumissionService.enregistrerChangementOnglet(100L, "etudiant@ept.sn"); // 5e : alerte ELEVE
        verify(alerteAntiTricheRepository).save(argThat(alerte -> alerte.getNiveau() == NiveauAlerte.ELEVE));
    }

    @Test
    @DisplayName("DS-02 : la fin de l'épreuve est plafonnée par la date de fermeture de l'évaluation")
    void sauvegarderReponses_ApresFermeture_Rejetee() {
        // 10 min écoulées sur 30, mais l'évaluation a fermé il y a 5 min (au-delà de la marge de 2 min)
        evaluation.setDateFermeture(MAINTENANT.minusMinutes(5));

        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(soumissionRepository.findById(100L)).thenReturn(Optional.of(soumission));
        when(amenagementTempsRepository.findByEtudiantIdAndEvaluationId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> soumissionService.sauvegarderReponses(100L, new SauvegardeReponsesRequest(), "etudiant@ept.sn"))
                .isInstanceOf(TempsEcouleException.class);
    }

    @Test
    @DisplayName("Démarrage : une évaluation TERMINEE est refusée même si la date de fermeture n'est pas passée")
    void demarrer_EvaluationTerminee_Refusee() {
        evaluation.setDateOuverture(MAINTENANT.minusHours(1));
        evaluation.setDateFermeture(MAINTENANT.plusHours(1));
        evaluation.setStatut(StatutEvaluation.TERMINEE);

        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation));

        assertThatThrownBy(() -> soumissionService.demarrerOuReprendre(10L, "etudiant@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Démarrage : un étudiant sans promotion ne peut pas passer une évaluation")
    void demarrer_EtudiantSansPromotion_Refuse() {
        evaluation.setDateOuverture(MAINTENANT.minusHours(1));
        evaluation.setDateFermeture(MAINTENANT.plusHours(1));
        evaluation.setStatut(StatutEvaluation.EN_COURS);
        evaluation.setPromotions(List.of(new Promotion("DIC2 Informatique", "2025/2026")));

        when(etudiantRepository.findByEmail("etudiant@ept.sn")).thenReturn(Optional.of(etudiant));
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation));

        assertThatThrownBy(() -> soumissionService.demarrerOuReprendre(10L, "etudiant@ept.sn"))
                .isInstanceOf(AccesRefuseException.class);
    }
}
