package sn.ept.eval.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Configuration réelle (src/main/resources/application.yml) chargée sans autre composant : un déploiement
 * où SPRING_PROFILES_ACTIVE est oublié ne doit pas basculer sur le profil dev (comptes de démonstration,
 * secret JWT publié dans le dépôt) mais rester sans profil et sans secret par défaut.
 * Le fichier est lu sur disque : celui de src/test/resources masque l'original dans le classpath de test.
 */
class ProfilParDefautTest {

    private static final String CONFIG_PRINCIPALE = "--spring.config.location=file:src/main/resources/application.yml";

    /** Contexte vide : seul l'environnement (fichiers de configuration, profils) est construit. */
    @Configuration(proxyBeanMethods = false)
    static class ContexteVide {
    }

    /** Arguments de ligne de commande, comme au lancement réel (priorité sur application.yml). */
    private ConfigurableEnvironment environnement(String... arguments) {
        try (ConfigurableApplicationContext contexte = new SpringApplicationBuilder(ContexteVide.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run(arguments)) {
            return contexte.getEnvironment();
        }
    }

    @BeforeEach
    void horsVariablesDuPoste() {
        // Sur un poste qui définit déjà ces variables, le cas « oubliées au déploiement » n'est pas reproductible
        assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null, "SPRING_PROFILES_ACTIVE défini sur ce poste");
        assumeTrue(System.getenv("JWT_SECRET") == null, "JWT_SECRET défini sur ce poste");
        assumeTrue(System.getenv("DB_PASSWORD") == null, "DB_PASSWORD défini sur ce poste");
    }

    @Test
    @DisplayName("Sans profil ni DB_PASSWORD, le démarrage échoue avant toute connexion à la base")
    void sansProfil_SansMotDePasse_DemarrageRefuse() {
        // Sans contrôle, Spring envoyait la chaîne « ${DB_PASSWORD} » comme mot de passe
        assertThatThrownBy(() -> environnement(CONFIG_PRINCIPALE))
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("Sans SPRING_PROFILES_ACTIVE, aucun profil n'est actif et aucun secret JWT n'est fourni")
    void sansProfil_NiDevNiSecret() {
        ConfigurableEnvironment env = environnement(CONFIG_PRINCIPALE, "--spring.datasource.password=fourni");

        assertThat(env.getActiveProfiles()).isEmpty();
        assertThatThrownBy(() -> env.getRequiredProperty("jwt.secret"))
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("Le profil dev demandé explicitement garde ses valeurs locales")
    void profilDevExplicite_SecretDeDev() {
        ConfigurableEnvironment env = environnement(CONFIG_PRINCIPALE, "--spring.profiles.active=dev");

        assertThat(env.getActiveProfiles()).containsExactly("dev");
        assertThat(env.getRequiredProperty("jwt.secret")).isNotBlank();
    }
}
