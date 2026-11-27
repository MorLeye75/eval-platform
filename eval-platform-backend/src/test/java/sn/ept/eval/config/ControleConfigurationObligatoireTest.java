package sn.ept.eval.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControleConfigurationObligatoireTest {

    private final ControleConfigurationObligatoire controle = new ControleConfigurationObligatoire();

    private StandardEnvironment environnement(String motDePasse) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of(ControleConfigurationObligatoire.PROPRIETE_MOT_DE_PASSE, motDePasse)));
        return env;
    }

    @Test
    @DisplayName("Un mot de passe dont la variable n'est pas définie empêche le démarrage")
    void variableAbsente_Refuse() {
        StandardEnvironment env = environnement("${VARIABLE_JAMAIS_DEFINIE_POUR_LE_TEST}");

        assertThatThrownBy(() -> controle.postProcessEnvironment(env, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("VARIABLE_JAMAIS_DEFINIE_POUR_LE_TEST");
    }

    @Test
    @DisplayName("Un mot de passe fourni, ou vide explicitement (H2), est accepté")
    void motDePasseFourni_Accepte() {
        assertThatCode(() -> controle.postProcessEnvironment(environnement("secret"), new SpringApplication()))
                .doesNotThrowAnyException();
        assertThatCode(() -> controle.postProcessEnvironment(environnement(""), new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sans propriété de mot de passe du tout, rien n'est imposé")
    void proprieteAbsente_Accepte() {
        assertThatCode(() -> controle.postProcessEnvironment(new StandardEnvironment(), new SpringApplication()))
                .doesNotThrowAnyException();
    }
}
