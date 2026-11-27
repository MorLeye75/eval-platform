package sn.ept.eval.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.service.NotificationService;

/**
 * Implémentation provisoire de {@link NotificationService} : elle journalise l'événement et ne crée aucune
 * notification.
 *
 * <p>{@link Fallback} plutôt que {@code @ConditionalOnMissingBean} : cette condition est évaluée à l'enregistrement des
 * beans, donc selon l'ordre de découverte des classes, et n'est fiable que dans une auto-configuration. Ici le bean
 * par défaut existe toujours, mais Spring ne l'injecte que si aucune autre implémentation n'est déclarée : celle du
 * module de notifications (DS-15) est préférée, quel que soit l'ordre dans lequel les beans sont enregistrés.
 */
@Configuration
public class NotificationParDefautConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationParDefautConfig.class);

    @Bean
    @Fallback
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
