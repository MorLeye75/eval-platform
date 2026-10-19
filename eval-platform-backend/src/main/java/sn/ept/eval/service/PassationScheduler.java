package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâches de fond de la passation : clôture des copies abandonnées et reprise des corrections bloquées.
 * Désactivable avec passation.taches-planifiees.actif=false (cas des tests).
 */
@Component
@ConditionalOnProperty(name = "passation.taches-planifiees.actif", havingValue = "true", matchIfMissing = true)
public class PassationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PassationScheduler.class);

    private final ClotureCopiesService clotureCopiesService;

    public PassationScheduler(ClotureCopiesService clotureCopiesService) {
        this.clotureCopiesService = clotureCopiesService;
    }

    @Scheduled(fixedDelayString = "${passation.taches-planifiees.intervalle-ms:60000}",
               initialDelayString = "${passation.taches-planifiees.intervalle-ms:60000}")
    public void executer() {
        // Deux appels distincts : un échec de l'un n'empêche pas l'autre
        try {
            int cloturees = clotureCopiesService.cloturerCopiesExpirees();
            if (cloturees > 0) {
                log.info("[Planifié] {} copie(s) clôturée(s) automatiquement", cloturees);
            }
        } catch (Exception e) {
            log.error("[Planifié] Échec de la clôture des copies expirées", e);
        }

        try {
            clotureCopiesService.relancerCorrectionsBloquees();
        } catch (Exception e) {
            log.error("[Planifié] Échec de la relance des corrections bloquées", e);
        }
    }
}
