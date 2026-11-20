package sn.ept.eval.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Configuration en vigueur (DS-18, section D) de bout en bout sur H2 : fidèle au code, sans aucun secret. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-configuration;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class AdminConfigurationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Environment environnement;

    @Test
    @DisplayName("La configuration reprend les valeurs réellement appliquées (code et propriétés), chaque ligne indique sa source")
    void valeursReelles() throws Exception {
        String corps = mockMvc.perform(get("/admin/configuration").header("Authorization", bearer("admin@ept.sn", "admin123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<String>) JsonPath.read(corps, "$.sections[*].titre"))
                .containsExactly("Passation", "Notation", "Sécurité des comptes", "Correction", "Administration");
        // valeurs du code : même source que les services qui les appliquent
        assertThat(valeur(corps, "Marge de tolérance après la fermeture")).isEqualTo(TempsEpreuveService.MARGE_TOLERANCE_MINUTES + " min");
        assertThat(valeur(corps, "Seuil de réussite")).isEqualTo("10 / 20");
        assertThat(valeur(corps, "Import CSV d'étudiants")).startsWith(ImportEtudiantsService.LIGNES_MAX + " lignes");
        assertThat(source(corps, "Seuil de réussite")).isEqualTo("CODE");
        // valeurs du déploiement : celles de la configuration de ce test
        long minutes = Long.parseLong(environnement.getProperty("jwt.expiration-ms")) / 60_000;
        assertThat(valeur(corps, "Durée de validité d'une session")).isEqualTo(minutes % 60 == 0 ? (minutes / 60) + " h" : minutes + " min");
        assertThat(valeur(corps, "Échecs de connexion avant blocage")).isEqualTo(environnement.getProperty("securite.login.max-echecs", "5"));
        assertThat(source(corps, "Échecs de connexion avant blocage")).isEqualTo("DEPLOIEMENT");
        assertThat(valeur(corps, "Correction des questions ouvertes")).contains("simulé");
    }

    @Test
    @DisplayName("Aucun secret dans la réponse : ni clé de signature, ni jeton Judge0, ni mot de passe ou adresse de base")
    void aucunSecret() throws Exception {
        String corps = mockMvc.perform(get("/admin/configuration").header("Authorization", bearer("admin@ept.sn", "admin123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        for (String cle : new String[]{"jwt.secret", "correction.judge0.token", "spring.datasource.password", "spring.datasource.url", "correction.judge0.url"}) {
            String secret = environnement.getProperty(cle);
            if (secret != null && secret.length() >= 6) {
                assertThat(corps).as("la valeur de " + cle + " ne doit pas apparaître").doesNotContain(secret);
            }
        }
        assertThat(corps.toLowerCase()).doesNotContain("password").doesNotContain("mot de passe de la base").doesNotContain("jdbc:");
    }

    @Test
    @DisplayName("Accès : enseignant 403, étudiant 403, sans jeton 401")
    void acces() throws Exception {
        mockMvc.perform(get("/admin/configuration").header("Authorization", bearer("prof@ept.sn", "prof123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/configuration").header("Authorization", bearer("etudiant@ept.sn", "etudiant123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/configuration")).andExpect(status().isUnauthorized());
    }

    private static java.util.Map<String, Object> parametre(String corps, String libelle) {
        List<java.util.Map<String, Object>> tous = JsonPath.read(corps, "$.sections[*].parametres[*]");
        return tous.stream().filter(p -> libelle.equals(p.get("libelle"))).findFirst()
                .orElseThrow(() -> new AssertionError("paramètre absent : " + libelle));
    }

    private static String valeur(String corps, String libelle) {
        return (String) parametre(corps, libelle).get("valeur");
    }

    private static String source(String corps, String libelle) {
        return (String) parametre(corps, libelle).get("source");
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }
}
