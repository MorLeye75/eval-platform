package sn.ept.eval.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration de l'Audit Trail JPA automatique.
 * Active les annotations @CreatedDate, @LastModifiedDate, @CreatedBy et @LastModifiedBy.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
public class JpaAuditingConfig {
}
