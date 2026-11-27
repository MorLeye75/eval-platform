package sn.ept.eval.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.repository.UtilisateurRepository;
import sn.ept.eval.security.services.RevocationJetonsService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Déconnexion (DS-14) : le jeton de la session fermée est révoqué côté serveur, les autres sessions du même compte
 * (autre appareil) restent ouvertes. Base H2 propre à cette classe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deconnexion;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class DeconnexionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RevocationJetonsService revocationJetonsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;

    @BeforeEach
    void setUp() {
        email = "etu-deco-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Fall", "Ibou", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), null));
    }

    private String bearer() throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"motDePasse\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }

    @Test
    @DisplayName("Après la déconnexion, le jeton de cette session est refusé (401), même avant son expiration")
    void jetonRevoque() throws Exception {
        String auth = bearer();
        mockMvc.perform(get("/auth/me").header("Authorization", auth)).andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header("Authorization", auth)).andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").header("Authorization", auth)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/profil").header("Authorization", auth)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Se déconnecter sur un appareil ne ferme pas la session ouverte sur un autre (épreuve en cours sur le PC)")
    void autreSessionIntacte() throws Exception {
        String telephone = bearer();
        String pc = bearer();

        mockMvc.perform(post("/auth/logout").header("Authorization", telephone)).andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").header("Authorization", pc)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Déconnexion sans jeton, ou avec un jeton déjà révoqué : 401, rien n'est enregistré")
    void sansJetonValide_401() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());

        String auth = bearer();
        mockMvc.perform(post("/auth/logout").header("Authorization", auth)).andExpect(status().isNoContent());
        mockMvc.perform(post("/auth/logout").header("Authorization", auth)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Purge : les révocations de jetons déjà expirés sont supprimées (la table ne grossit pas sans fin)")
    void purgeDesJetonsExpires() {
        revocationJetonsService.revoquer("jeton-expire", LocalDateTime.now().minusMinutes(1));
        revocationJetonsService.revoquer("jeton-valide", LocalDateTime.now().plusHours(1));

        revocationJetonsService.purger();

        assertThat(jdbcTemplate.queryForList("select jti from jetons_revoques", String.class))
                .contains("jeton-valide").doesNotContain("jeton-expire");
    }
}
