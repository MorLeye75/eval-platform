package sn.ept.eval.service.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import sn.ept.eval.dto.admin.ConfigurationSystemeResponse;
import sn.ept.eval.service.ConfigurationSystemeService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Câblage du connecteur (DS-08) : avec {@code correction.ia.fournisseur=llm}, le correcteur actif est le connecteur
 * externe et non le simulé ; la page de configuration le dit, sans jamais montrer la clé ni l'adresse.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:correcteur-llm-config;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "correction.ia.fournisseur=llm",
        "correction.ia.llm.cle=cle-secrete-de-test-0123456789",
        "correction.ia.llm.url=http://127.0.0.1:9/v1/chat/completions",
        "correction.ia.llm.modele=modele-de-test"
})
class CorrecteurIAConfigurationTest {

    @Autowired private ApplicationContext contexte;
    @Autowired private ConfigurationSystemeService configurationService;

    @Test
    @DisplayName("Fournisseur « llm » : un seul correcteur dans le contexte, le connecteur externe (pas le simulé)")
    void connecteurActif() {
        assertThat(contexte.getBeansOfType(CorrecteurIA.class).values()).hasSize(1).first().isInstanceOf(CorrecteurIALlm.class);
        assertThat(contexte.getBeansOfType(CorrecteurIASimule.class)).isEmpty();
    }

    @Test
    @DisplayName("La page de configuration annonce le modèle externe et l'envoi des réponses, sans clé ni adresse")
    void configurationHonnete() {
        String valeur = configurationService.configuration().sections().stream()
                .flatMap(s -> s.parametres().stream())
                .filter(p -> p.libelle().equals("Correction des questions ouvertes")).findFirst().orElseThrow().valeur();

        assertThat(valeur).contains("modele-de-test").contains("envoyé au fournisseur");
        ConfigurationSystemeResponse toute = configurationService.configuration();
        assertThat(toute.toString()).doesNotContain("cle-secrete-de-test").doesNotContain("127.0.0.1");
    }
}
