package sn.ept.eval.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Choix de l'envoi d'e-mails. Spring Boot ne crée le {@link JavaMailSender} que si {@code spring.mail.host} est défini
 * (variable {@code SPRING_MAIL_HOST}) : on envoie alors par SMTP, sinon les liens sont journalisés.
 * {@link Fallback} : le journal n'est injecté que si aucune autre implémentation n'existe (voir NotificationParDefautConfig).
 */
@Configuration
public class EmailConfig {

    static final String EXECUTEUR = "emailTaskExecutor";

    @Bean
    @Fallback
    public EnvoiEmail envoiEmailJournal() {
        return new EnvoiEmailJournal();
    }

    /**
     * Même condition que l'auto-configuration du JavaMailSender. Pas de {@code @ConditionalOnBean} : dans une
     * configuration applicative, il est évalué avant les auto-configurations et serait toujours faux.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    public EnvoiEmail envoiEmailSmtp(JavaMailSender mailSender,
                                     @Value("${app.mail.expediteur:no-reply@ept.sn}") String expediteur) {
        return new EnvoiEmailSmtp(mailSender, expediteur);
    }

    /** Peu d'e-mails, et jamais en rafale (demandes limitées par compte) : deux threads suffisent. */
    @Bean(name = EXECUTEUR)
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Email-");
        executor.initialize();
        return executor;
    }
}
