package sn.ept.eval.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.SoumissionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Traitements de fond sur les copies, exécutés par PassationScheduler : clôture des copies abandonnées
 * et reprise des corrections perdues.
 */
@Service
@Transactional
public class ClotureCopiesService {

    private static final Logger log = LoggerFactory.getLogger(ClotureCopiesService.class);

    /** Au-delà de ce délai sans modification, une copie SOUMIS est considérée comme bloquée. */
    public static final int DELAI_CORRECTION_BLOQUEE_MINUTES = 5;

    private final SoumissionRepository soumissionRepository;
    private final TempsEpreuveService tempsEpreuveService;
    private final CorrectionDeclencheur correctionDeclencheur;
    private final EntityManager entityManager;

    public ClotureCopiesService(SoumissionRepository soumissionRepository,
                                TempsEpreuveService tempsEpreuveService,
                                CorrectionDeclencheur correctionDeclencheur,
                                EntityManager entityManager) {
        this.soumissionRepository = soumissionRepository;
        this.tempsEpreuveService = tempsEpreuveService;
        this.correctionDeclencheur = correctionDeclencheur;
        this.entityManager = entityManager;
    }

    /**
     * Les copies EN_COURS dont le temps, marge comprise, est écoulé sont figées à leur dernier auto-save
     * puis corrigées. Cas typique : l'étudiant a fermé son navigateur.
     *
     * @return le nombre de copies clôturées
     */
    public int cloturerCopiesExpirees() {
        int nbCloturees = 0;
        for (Soumission soumission : soumissionRepository.findByStatut(StatutSoumission.EN_COURS)) {
            if (!tempsEpreuveService.estExpiree(soumission)) continue;

            // Relue sous verrou : une soumission ou une reprise concurrente a pu la clôturer entre-temps
            entityManager.refresh(soumission, LockModeType.PESSIMISTIC_WRITE);
            if (soumission.getStatut() == StatutSoumission.EN_COURS && tempsEpreuveService.estExpiree(soumission)) {
                cloturerExpiree(soumission);
                log.info("Clôture automatique de la soumission ID={} (temps écoulé)", soumission.getId());
                nbCloturees++;
            }
        }
        return nbCloturees;
    }

    /**
     * Fige une copie expirée à son dernier auto-save et lance sa correction après le commit, sans alerte
     * anti-triche (l'étudiant n'a rien envoyé hors délai). L'appelant doit détenir le verrou sur la copie.
     */
    public void cloturerExpiree(Soumission soumission) {
        soumission.setStatut(StatutSoumission.SOUMIS);
        soumission.setDateRemise(tempsEpreuveService.finEpreuve(soumission));
        correctionDeclencheur.apresCommit(soumission.getId());
    }

    /**
     * Copies restées SOUMIS : la correction asynchrone a échoué ou le serveur a redémarré
     * entre la soumission et la correction.
     *
     * @return le nombre de corrections relancées
     */
    public int relancerCorrectionsBloquees() {
        LocalDateTime seuil = tempsEpreuveService.maintenant().minusMinutes(DELAI_CORRECTION_BLOQUEE_MINUTES);
        List<Soumission> bloquees = soumissionRepository.findByStatutAndDateModificationBefore(StatutSoumission.SOUMIS, seuil);
        bloquees.forEach(s -> {
            log.warn("Relance de la correction de la soumission ID={} (bloquée en SOUMIS)", s.getId());
            correctionDeclencheur.apresCommit(s.getId());
        });
        return bloquees.size();
    }
}
