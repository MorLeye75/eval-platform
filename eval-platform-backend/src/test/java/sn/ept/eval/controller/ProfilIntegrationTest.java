package sn.ept.eval.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profil de l'utilisateur connecté : identité en lecture seule (gérée par l'administrateur), changement de son
 * mot de passe. Base H2 propre à cette classe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:profil;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class ProfilIntegrationTest {

    private static final String MOT_DE_PASSE = "ancienMotDePasse";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;
    private String matricule;

    @BeforeEach
    void setUp() {
        String suffixe = UUID.randomUUID().toString();
        email = "etu-profil-" + suffixe + "@ept.sn";
        matricule = "ETU-" + suffixe;
        utilisateurRepository.save(new Etudiant("Diop", "Awa", email, passwordEncoder.encode(MOT_DE_PASSE), matricule,
                promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
    }

    private ResultActions connexion(String motDePasse) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"));
    }

    private String bearer() throws Exception {
        String reponse = connexion(MOT_DE_PASSE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }

    private ResultActions changer(String auth, String actuel, String nouveau) throws Exception {
        return mockMvc.perform(put("/profil/mot-de-passe").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasseActuel\":\"" + actuel + "\",\"nouveauMotDePasse\":\"" + nouveau + "\"}"));
    }

    @Test
    @DisplayName("Profil d'un étudiant : identité, matricule et promotion")
    void profilEtudiant() throws Exception {
        mockMvc.perform(get("/profil").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Diop"))
                .andExpect(jsonPath("$.prenom").value("Awa"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ETUDIANT"))
                .andExpect(jsonPath("$.numEtudiant").value(matricule))
                .andExpect(jsonPath("$.promotion").value("DIC2 Informatique"))
                .andExpect(jsonPath("$.motDePasse").doesNotExist());
    }

    @Test
    @DisplayName("Profil d'un enseignant : pas de matricule ni de promotion")
    void profilEnseignant() throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prof@ept.sn\",\"motDePasse\":\"prof123\"}"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/profil").header("Authorization", "Bearer " + JsonPath.read(reponse, "$.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ENSEIGNANT"))
                .andExpect(jsonPath("$.numEtudiant").doesNotExist())
                .andExpect(jsonPath("$.promotion").doesNotExist());
    }

    @Test
    @DisplayName("Sans jeton : 401")
    void sansJeton_401() throws Exception {
        mockMvc.perform(get("/profil")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/profil/mot-de-passe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasseActuel\":\"a\",\"nouveauMotDePasse\":\"bbbbbbbb\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Changement réussi : 204, la connexion se fait avec le nouveau mot de passe, plus avec l'ancien")
    void changementReussi() throws Exception {
        changer(bearer(), MOT_DE_PASSE, "nouveauMotDePasse").andExpect(status().isNoContent());

        connexion("nouveauMotDePasse").andExpect(status().isOk());
        connexion(MOT_DE_PASSE).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Mot de passe actuel incorrect : 400 (pas 401, qui déconnecterait), mot de passe inchangé")
    void actuelIncorrect_400() throws Exception {
        changer(bearer(), "faux", "nouveauMotDePasse")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("actuel")));

        connexion(MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nouveau mot de passe trop court, trop long (BCrypt : 72 octets) ou identique : 400")
    void nouveauInvalide_400() throws Exception {
        String auth = bearer();
        changer(auth, MOT_DE_PASSE, "court").andExpect(status().isBadRequest());
        changer(auth, MOT_DE_PASSE, "é".repeat(40)).andExpect(status().isBadRequest()); // 40 caractères, 80 octets
        changer(auth, MOT_DE_PASSE, MOT_DE_PASSE).andExpect(status().isBadRequest());

        connexion(MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mots de passe actuels faux à répétition : bloqué (429) comme la connexion, pas de force brute avec un jeton volé")
    void forceBrute_429() throws Exception {
        String auth = bearer();
        for (int i = 0; i < 5; i++) {
            changer(auth, "faux" + i, "nouveauMotDePasse").andExpect(status().isBadRequest());
        }

        changer(auth, MOT_DE_PASSE, "nouveauMotDePasse").andExpect(status().isTooManyRequests());
    }
}
