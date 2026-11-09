package sn.ept.eval.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.service.NotificationService;

/**
 * Implémentation provisoire de {@link NotificationService} : elle journalise l'événement et ne crée aucune
 * notification. Elle s'efface dès qu'un autre bean {@code NotificationService} est déclaré (module de Mor, DS-15).
 */
@Configuration
public class NotificationParDefautConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationParDefautConfig.class);

    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    public NotificationService notificationServiceParDefaut() {
        return new NotificationServiceParDefaut();
    }

    /** Public pour pouvoir être instancié dans les tests. */
    public static class NotificationServiceParDefaut implements NotificationService {
        @Override
        public void evaluationOuverte(Evaluation evaluation) {
            log.debug("[Notification] évaluation ouverte ID={} (aucune notification : service non implémenté)", evaluation.getId());
        }

        @Override
        public void resultatsPublies(Evaluation evaluation) {
            log.debug("[Notification] résultats publiés ID={} (aucune notification : service non implémenté)", evaluation.getId());
        }

        @Override
        public void resultatPublie(Soumission copie) {
            log.debug("[Notification] résultat publié, copie ID={} (aucune notification : service non implémenté)", copie.getId());
        }
    }
}
