package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.AlerteAntiTriche;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.AlerteAntiTricheRepository;
import sn.ept.eval.repository.SoumissionRepository;

/**
 * Détection et journalisation des comportements suspects pendant une épreuve :
 * changements d'onglet / sorties de plein écran et soumissions hors délai.
 */
@Service
@Transactional
public class AntiTricheService {

    private static final Logger log = LoggerFactory.getLogger(AntiTricheService.class);

    public static final int SEUIL_ALERTE_MODEREE = 3;
    public static final int SEUIL_ALERTE_ELEVEE = 5;

    private final SoumissionRepository soumissionRepository;
    private final AlerteAntiTricheRepository alerteAntiTricheRepository;
    private final SoumissionAccesService soumissionAccesService;
    private final TempsEpreuveService tempsEpreuveService;

    public AntiTricheService(SoumissionRepository soumissionRepository,
                             AlerteAntiTricheRepository alerteAntiTricheRepository,
                             SoumissionAccesService soumissionAccesService,
                             TempsEpreuveService tempsEpreuveService) {
        this.soumissionRepository = soumissionRepository;
        this.alerteAntiTricheRepository = alerteAntiTricheRepository;
        this.soumissionAccesService = soumissionAccesService;
        this.tempsEpreuveService = tempsEpreuveService;
    }

    /**
     * Journaliser une sortie d'écran / changement d'onglet signalé par le navigateur de l'étudiant.
     * Une alerte est levée au franchissement de chaque seuil (et non à chaque événement suivant).
     */
    public void enregistrerChangementOnglet(Long soumissionId, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        // Verrou pessimiste : des signalements simultanés ne doivent pas perdre d'incrément
        Soumission soumission = soumissionRepository.findByIdForUpdate(soumissionId)
                .orElseThrow(() -> SoumissionAccesService.soumissionIntrouvable(soumissionId));
        soumissionAccesService.verifierProprietaire(soumission, etudiant);

        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            return;
        }

        int nb = soumission.getNbChangementsOnglet() != null ? soumission.getNbChangementsOnglet() + 1 : 1;
        soumission.setNbChangementsOnglet(nb);
        soumissionRepository.save(soumission);

        if (nb == SEUIL_ALERTE_MODEREE || nb == SEUIL_ALERTE_ELEVEE) {
            lever(soumission, nb == SEUIL_ALERTE_ELEVEE ? NiveauAlerte.ELEVE : NiveauAlerte.MODERE,
                    "Suspicion de triche : " + nb + " changements d'onglets ou sorties de plein écran détectés.");
        }
    }

    /** Soumission reçue au-delà de la marge de tolérance (copie figée à son dernier auto-save). */
    public void signalerSoumissionTardive(Soumission soumission) {
        log.warn("[Anti-Triche] Dépassement temporel critique pour la soumission ID={}", soumission.getId());
        lever(soumission, NiveauAlerte.ELEVE,
                "Soumission tardive : le temps imparti a été dépassé au-delà de la marge de tolérance.");
    }

    private void lever(Soumission soumission, NiveauAlerte niveau, String description) {
        AlerteAntiTriche alerte = new AlerteAntiTriche(description, niveau, soumission);
        alerte.setDateDetection(tempsEpreuveService.maintenant());
        alerteAntiTricheRepository.save(alerte);
    }
}
