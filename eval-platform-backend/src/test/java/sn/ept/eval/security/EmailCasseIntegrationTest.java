package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'email d'un compte ne dépend pas de la casse : un téléphone qui met une majuscule ne doit pas faire échouer la
 * connexion, et deux comptes ne peuvent pas différer par la seule casse. Base H2 propre à cette classe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:email-casse;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class EmailCasseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int connexion(String email, String motDePasse) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("Connexion avec des majuscules ou des espaces autour de l'email : acceptée, email renvoyé en minuscules")
    void connexionInsensibleALaCasse() throws Exception {
        assertThat(connexion("Prof@ept.sn", "prof123")).isEqualTo(200);
        assertThat(connexion("PROF@EPT.SN", "prof123")).isEqualTo(200);
        assertThat(connexion("  prof@ept.sn ", "prof123")).isEqualTo(200);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"PROF@EPT.SN\",\"motDePasse\":\"prof123\"}"))
                .andExpect(jsonPath("$.email").value("prof@ept.sn"));
    }

    @Test
    @DisplayName("Compte créé avec des majuscules : enregistré en minuscules, sans espaces")
    void emailNormaliseALaCreation() {
        String suffixe = UUID.randomUUID().toString();
        Etudiant e = utilisateurRepository.save(new Etudiant("Ba", "Coumba", "  Coumba.BA-" + suffixe + "@EPT.sn ",
                passwordEncoder.encode("motDePasse"), "ETU-" + suffixe, null));

        assertThat(jdbcTemplate.queryForObject("select email from utilisateurs where id = ?", String.class, e.getId()))
                .isEqualTo("coumba.ba-" + suffixe + "@ept.sn");
    }

    @Test
    @DisplayName("Deux comptes qui ne diffèrent que par la casse de l'email : le second est refusé")
    void uniciteInsensibleALaCasse() {
        String suffixe = UUID.randomUUID().toString();
        utilisateurRepository.saveAndFlush(new Etudiant("A", "A", "double-" + suffixe + "@ept.sn", "x", "ETU-A" + suffixe, null));

        assertThatThrownBy(() -> utilisateurRepository.saveAndFlush(
                new Etudiant("B", "B", "DOUBLE-" + suffixe + "@EPT.SN", "x", "ETU-B" + suffixe, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
