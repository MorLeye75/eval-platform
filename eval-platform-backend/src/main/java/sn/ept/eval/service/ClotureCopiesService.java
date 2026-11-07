package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.SoumissionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Traitements de fond sur les copies, exécutés par PassationScheduler : clôture des copies abandonnées
 * et reprise des corrections perdues.
 */
@Service
public class ClotureCopiesService {

    private static final Logger log = LoggerFactory.getLogger(ClotureCopiesService.class);

    /** Au-delà de ce délai sans modification, une copie SOUMIS est considérée comme bloquée. */
    public static final int DELAI_CORRECTION_BLOQUEE_MINUTES = 5;

    private final SoumissionRepository soumissionRepository;
    private final TempsEpreuveService tempsEpreuveService;
    private final CorrectionDeclencheur correctionDeclencheur;
    /** Une transaction courte par copie clôturée : verrou relâché aussitôt, échec isolé. */
    private final TransactionTemplate transactionParCopie;

    public ClotureCopiesService(SoumissionRepository soumissionRepository,
                                TempsEpreuveService tempsEpreuveService,
                                CorrectionDeclencheur correctionDeclencheur,
                                PlatformTransactionManager transactionManager) {
        this.soumissionRepository = soumissionRepository;
        this.tempsEpreuveService = tempsEpreuveService;
        this.correctionDeclencheur = correctionDeclencheur;
        this.transactionParCopie = new TransactionTemplate(transactionManager);
        this.transactionParCopie.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Les copies EN_COURS dont le temps, marge comprise, est écoulé sont figées à leur dernier auto-save
     * puis corrigées. Cas typique : l'étudiant a fermé son navigateur.
     *
     * Les copies expirées sont repérées en mémoire (évaluations et aménagements chargés en lot), puis chacune
     * est clôturée dans sa propre transaction : les verrous ne s'accumulent pas sur tout le lot, et une copie
     * verrouillée ailleurs (soumission en cours…) est simplement reprise au passage suivant.
     *
     * @return le nombre de copies clôturées
     */
    public int cloturerCopiesExpirees() {
        List<Soumission> enCours = soumissionRepository.findByStatutAvecEvaluation(StatutSoumission.EN_COURS);
        Map<Long, Integer> durees = tempsEpreuveService.dureesEffectivesMinutes(enCours);

        int nbCloturees = 0;
        for (Soumission candidate : enCours) {
            if (!tempsEpreuveService.estExpiree(candidate, durees.get(candidate.getId()))) continue;
            Long id = candidate.getId();
            try {
                if (Boolean.TRUE.equals(transactionParCopie.execute(tx -> cloturerSiToujoursExpiree(id)))) {
                    nbCloturees++;
                }
            } catch (RuntimeException e) {
                log.warn("Clôture de la soumission ID={} reportée au prochain passage : {}", id, e.getMessage());
            }
        }
        return nbCloturees;
    }

    /** Relue sous verrou : une soumission, une reprise ou un aménagement accordé a pu intervenir entre-temps. */
    private boolean cloturerSiToujoursExpiree(Long soumissionId) {
        Soumission soumission = soumissionRepository.findByIdForUpdate(soumissionId).orElse(null);
        if (soumission == null || soumission.getStatut() != StatutSoumission.EN_COURS) return false;
        int duree = tempsEpreuveService.dureeEffectiveMinutes(soumission);
        if (!tempsEpreuveService.estExpiree(soumission, duree)) return false;
        cloturerExpiree(soumission, duree);
        log.info("Clôture automatique de la soumission ID={} (temps écoulé)", soumissionId);
        return true;
    }

    /**
     * Fige une copie expirée à son dernier auto-save et lance sa correction après le commit, sans alerte
     * anti-triche (l'étudiant n'a rien envoyé hors délai). L'appelant doit détenir le verrou sur la copie.
     */
    @Transactional
    public void cloturerExpiree(Soumission soumission) {
        cloturerExpiree(soumission, tempsEpreuveService.dureeEffectiveMinutes(soumission));
    }

    private void cloturerExpiree(Soumission soumission, int dureeEffectiveMinutes) {
        soumission.setStatut(StatutSoumission.SOUMIS);
        soumission.setDateRemise(tempsEpreuveService.finEpreuve(soumission, dureeEffectiveMinutes));
        correctionDeclencheur.apresCommit(soumission.getId());
    }

    /**
     * Copies restées SOUMIS : la correction asynchrone a échoué ou le serveur a redémarré
     * entre la soumission et la correction.
     *
     * @return le nombre de corrections relancées
     */
    @Transactional
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
