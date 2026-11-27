package sn.ept.eval.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuse de démarrer si le mot de passe de la base n'est pas fourni (DB_PASSWORD absent hors profil dev).
 *
 * Spring Boot ne signale pas un placeholder non résolu dans spring.datasource.* : il envoie la chaîne
 * « ${DB_PASSWORD} » telle quelle comme mot de passe, si bien qu'un PostgreSQL sans authentification
 * accepterait la connexion. Le contrôle a lieu avant la création des beans, donc avant que Flyway
 * ne se connecte. Un mot de passe vide fourni explicitement (H2 en dev et en test) reste accepté.
 *
 * Enregistré dans META-INF/spring.factories.
 */
public class ControleConfigurationObligatoire implements EnvironmentPostProcessor, Ordered {

    static final String PROPRIETE_MOT_DE_PASSE = "spring.datasource.password";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            // Résolution stricte : lève une exception si un placeholder n'a pas de valeur
            environment.getProperty(PROPRIETE_MOT_DE_PASSE);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Mot de passe de la base manquant : définir DB_PASSWORD "
                    + "(ou SPRING_PROFILES_ACTIVE=dev sur un poste de développement). " + e.getMessage(), e);
        }
    }

    /** Après le chargement des fichiers application.yml (ConfigDataEnvironmentPostProcessor). */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
