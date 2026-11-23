package sn.ept.eval.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;
import sn.ept.eval.service.email.EnvoiEmail;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mot de passe oublié : lien de réinitialisation par e-mail (envoi simulé), jeton à usage unique de 30 minutes. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reinitialisation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.frontend-url=https://eval.example"})
@AutoConfigureMockMvc
class ReinitialisationMotDePasseIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String MOT_DE_PASSE = "ancienMotDePasse";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EnvoiEmail envoiEmail;

    private String email;

    @BeforeEach
    void compte() {
        reset(envoiEmail);
        email = "reinit-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Diop", "Awa", email, passwordEncoder.encode(MOT_DE_PASSE),
                "N-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
    }

    // ---------------------------------------------------------------- demande

    @Test
    @DisplayName("Demande pour un compte existant : 202, un e-mail avec le lien du front ; le jeton n'est jamais stocké en clair")
    void demandeCompteExistant() throws Exception {
        demander(email.toUpperCase()).andExpect(status().isAccepted());

        String jeton = jetonEnvoye();
        assertThat(jeton).hasSizeGreaterThanOrEqualTo(40);
        Integer enClair = jdbcTemplate.queryForObject(
                "select count(*) from jetons_reinitialisation where empreinte = ?", Integer.class, jeton);
        assertThat(enClair).isZero();
    }

    @Test
    @DisplayName("Compte inconnu ou désactivé : même réponse 202, aucun e-mail (on ne révèle pas qui a un compte)")
    void compteInconnuOuDesactive() throws Exception {
        demander("personne-" + UUID.randomUUID() + "@ept.sn").andExpect(status().isAccepted());
        jdbcTemplate.update("update utilisateurs set actif = false where email = ?", email);
        demander(email).andExpect(status().isAccepted());

        verify(envoiEmail, never()).envoyerLienReinitialisation(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Plus de 3 demandes en 15 minutes pour un compte : toujours 202, mais plus d'e-mail")
    void demandesLimitees() throws Exception {
        for (int i = 0; i < 5; i++) demander(email).andExpect(status().isAccepted());
        verify(envoiEmail, times(3)).envoyerLienReinitialisation(eq(email), anyString(), anyString());
    }

    @Test
    @DisplayName("E-mail absent ou mal formé : 400")
    void demandeInvalide() throws Exception {
        mockMvc.perform(post("/auth/mot-de-passe-oublie").contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        demander("pas-un-email").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- réinitialisation

    @Test
    @DisplayName("Lien valide : 204, nouveau mot de passe accepté, ancien refusé, sessions ouvertes coupées ; lien à usage unique")
    void reinitialisation() throws Exception {
        String ancienneSession = bearer(MOT_DE_PASSE);
        demander(email).andExpect(status().isAccepted());
        String jeton = jetonEnvoye();

        Thread.sleep(1100); // le jeton de session est daté à la seconde
        reinitialiser(jeton, "NouveauMotDePasse1").andExpect(status().isNoContent());

        connexion(MOT_DE_PASSE).andExpect(status().isUnauthorized());
        connexion("NouveauMotDePasse1").andExpect(status().isOk()).andExpect(jsonPath("$.motDePasseProvisoire").value(false));
        mockMvc.perform(get("/profil").header("Authorization", ancienneSession)).andExpect(status().isUnauthorized());

        reinitialiser(jeton, "EncoreUnAutre2").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        connexion("NouveauMotDePasse1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mot de passe provisoire attribué par l'administrateur : levé par la réinitialisation")
    void leveLeMotDePasseProvisoire() throws Exception {
        jdbcTemplate.update("update utilisateurs set mot_de_passe_provisoire = true where email = ?", email);
        demander(email).andExpect(status().isAccepted());
        reinitialiser(jetonEnvoye(), "NouveauMotDePasse1").andExpect(status().isNoContent());
        connexion("NouveauMotDePasse1").andExpect(jsonPath("$.motDePasseProvisoire").value(false));
    }

    @Test
    @DisplayName("Lien expiré (30 min), inconnu, ou remplacé par une demande plus récente : 400 JETON_INVALIDE")
    void liensRefuses() throws Exception {
        demander(email).andExpect(status().isAccepted());
        String premier = jetonEnvoye();
        reset(envoiEmail);
        demander(email).andExpect(status().isAccepted());
        String second = jetonEnvoye();

        reinitialiser(premier, "NouveauMotDePasse1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        reinitialiser("inconnu-" + UUID.randomUUID(), "NouveauMotDePasse1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));

        jdbcTemplate.update("update jetons_reinitialisation set expire_le = timestamp '2000-01-01 00:00:00' "
                + "where utilisateur_id = (select id from utilisateurs where email = ?)", email);
        reinitialiser(second, "NouveauMotDePasse1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        connexion(MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nouveau mot de passe trop court ou trop long (72 octets) : 400, et le lien reste utilisable")
    void motDePasseInvalide() throws Exception {
        demander(email).andExpect(status().isAccepted());
        String jeton = jetonEnvoye();

        reinitialiser(jeton, "court").andExpect(status().isBadRequest());
        reinitialiser(jeton, "é".repeat(40)).andExpect(status().isBadRequest());
        reinitialiser(jeton, "NouveauMotDePasse1").andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- outils

    private ResultActions demander(String adresse) throws Exception {
        return mockMvc.perform(post("/auth/mot-de-passe-oublie").contentType(JSON)
                .content("{\"email\":\"" + adresse + "\"}"));
    }

    private ResultActions reinitialiser(String jeton, String motDePasse) throws Exception {
        return mockMvc.perform(post("/auth/reinitialiser-mot-de-passe").contentType(JSON)
                .content("{\"token\":\"" + jeton + "\",\"motDePasse\":\"" + motDePasse + "\"}"));
    }

    /** Jeton extrait du lien du dernier e-mail envoyé ; le lien pointe vers la page du front. */
    private String jetonEnvoye() {
        ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
        verify(envoiEmail, org.mockito.Mockito.atLeastOnce()).envoyerLienReinitialisation(eq(email), eq("Awa"), lien.capture());
        String dernier = lien.getValue();
        assertThat(dernier).startsWith("https://eval.example/reset-password/");
        return dernier.substring("https://eval.example/reset-password/".length());
    }

    private ResultActions connexion(String motDePasse) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"));
    }

    private String bearer(String motDePasse) throws Exception {
        String reponse = connexion(motDePasse).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }
}
