package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie de bout en bout les réponses d'erreur HTTP (format RFC 7807) et les règles d'accès.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecuriteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Sans jeton : 401 au format ProblemDetail")
    void sansJeton_401ProblemDetail() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
    }

    @Test
    @DisplayName("Mauvais mot de passe : 401 générique au format ProblemDetail")
    void loginInvalide_401ProblemDetail() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"etudiant@ept.sn\",\"motDePasse\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTIFICATION_ECHOUEE"));
    }

    @Test
    @DisplayName("JSON illisible : reste un 400 (et non un 500 du gestionnaire générique)")
    void jsonIllisible_400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{pas du json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Console H2 non exposée hors profil h2-dev")
    void consoleH2_NonPublique() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
    }
}
