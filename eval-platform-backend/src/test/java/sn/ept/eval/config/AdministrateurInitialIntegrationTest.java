package sn.ept.eval.config;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import sn.ept.eval.repository.AdministrateurRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Premier administrateur d'une installation neuve (production : aucun compte de démonstration). Créé au démarrage
 * depuis ADMIN_INITIAL_EMAIL et ADMIN_INITIAL_MOT_DE_PASSE, avec un mot de passe provisoire à changer à la première
 * connexion ; jamais recréé ensuite.
 */
@SpringBootTest(properties = {
        "app.admin-initial.email=direction@ept.sn",
        "app.admin-initial.mot-de-passe=MotDePasseInitial2026",
        "spring.datasource.url=jdbc:h2:mem:admin-initial;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
class AdministrateurInitialIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdministrateurRepository administrateurRepository;
    @Autowired private AdministrateurInitialInitializer initializer;

    @Test
    @DisplayName("Base sans administrateur : il est créé, se connecte avec un mot de passe provisoire et doit le changer avant tout")
    void premierAdministrateurCree() throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"direction@ept.sn\",\"motDePasse\":\"MotDePasseInitial2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.motDePasseProvisoire").value(true))
                .andReturn().getResponse().getContentAsString();
        String jeton = "Bearer " + JsonPath.read(reponse, "$.token");

        // Mot de passe provisoire : rien d'autre que le profil tant qu'il n'est pas changé (DS-16)
        mockMvc.perform(get("/admin/utilisateurs").header("Authorization", jeton)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un administrateur existe déjà : rien n'est recréé au redémarrage, même si les variables sont encore là")
    void jamaisRecree() throws Exception {
        long avant = administrateurRepository.count();
        initializer.run();
        assertThat(administrateurRepository.count()).isEqualTo(avant).isEqualTo(1);
    }
}
