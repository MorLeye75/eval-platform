package sn.ept.eval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Configuration de l'Audit Trail JPA automatique.
 * Active les annotations @CreatedDate, @LastModifiedDate, @CreatedBy et @LastModifiedBy.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware", dateTimeProviderRef = "horlogeAudit")
public class JpaAuditingConfig {

    /**
     * Dates d'audit lues sur l'horloge de l'application (et non l'heure système), comme les règles de temps
     * des épreuves : la relance des corrections compare date_modification à un seuil calculé avec cette horloge.
     */
    @Bean
    public DateTimeProvider horlogeAudit(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
