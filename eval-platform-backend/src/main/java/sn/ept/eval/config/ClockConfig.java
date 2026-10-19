package sn.ept.eval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Horloge unique de l'application : toutes les règles temporelles (fenêtre d'ouverture, durée,
 * marge de tolérance) l'utilisent, ce qui permet de la figer dans les tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
