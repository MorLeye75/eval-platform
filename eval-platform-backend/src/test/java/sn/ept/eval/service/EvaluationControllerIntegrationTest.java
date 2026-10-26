package sn.ept.eval.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours HTTP complet de DS-03, DS-04 et DS-06 sur H2 : sécurité par rôle, codes d'erreur RFC 7807
 * (avec le champ code) et cycle de vie BROUILLON → PUBLIEE → BROUILLON.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PromotionRepository promotionRepository;

    @Test
    @DisplayName("Sécurité : 401 sans jeton, 403 pour un étudiant")
    void securite() throws Exception {
        mockMvc.perform(get("/enseignant/evaluations")).andExpect(status().isUnauthorized());

        String etudiant = "Bearer " + jeton(nouvelEtudiantDic2(), "motDePasse");
        mockMvc.perform(get("/enseignant/evaluations").header("Authorization", etudiant)).andExpect(status().isForbidden());
        mockMvc.perform(post("/enseignant/evaluations").header("Authorization", etudiant)
                        .contentType(MediaType.APPLICATION_JSON).content(corps(promotionId(), "Titre")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Création : 201 + Location, brouillon avec ses questions et leurs bonnes réponses (vue enseignant)")
    void creation() throws Exception {
        String prof = "Bearer " + jeton("prof@ept.sn", "prof123");

        mockMvc.perform(post("/enseignant/evaluations").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content(corps(promotionId(), "Réseaux IT")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.nbQuestions").value(2))
                .andExpect(jsonPath("$.questions[0].ordre").value(1))
                .andExpect(jsonPath("$.questions[0].choix[0].estCorrect").value(true));
    }

    @Test
    @DisplayName("Création invalide : 400 (champ manquant) et 404 (promotion inconnue), au format ProblemDetail")
    void creationInvalide() throws Exception {
        String prof = "Bearer " + jeton("prof@ept.sn", "prof123");

        mockMvc.perform(post("/enseignant/evaluations").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titre\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/enseignant/evaluations").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content(corps(999_999L, "Promo fantôme")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cycle de vie : publier, modification refusée (409), dépublier, modifier, supprimer")
    void cycleDeVie() throws Exception {
        String prof = "Bearer " + jeton("prof@ept.sn", "prof123");
        Integer id = creer(prof, "Cycle de vie");

        mockMvc.perform(post("/enseignant/evaluations/" + id + "/publier").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PUBLIEE"));

        mockMvc.perform(put("/enseignant/evaluations/" + id).header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content(corps(promotionId(), "Titre modifié")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NON_MODIFIABLE"));

        mockMvc.perform(post("/enseignant/evaluations/" + id + "/publier").header("Authorization", prof))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NON_MODIFIABLE"));

        mockMvc.perform(post("/enseignant/evaluations/" + id + "/depublier").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"));

        mockMvc.perform(put("/enseignant/evaluations/" + id).header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content(corps(promotionId(), "Titre modifié")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Titre modifié"))
                .andExpect(jsonPath("$.version").isNumber());

        mockMvc.perform(delete("/enseignant/evaluations/" + id).header("Authorization", prof))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/enseignant/evaluations/" + id).header("Authorization", prof))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un autre enseignant n'accède pas à l'évaluation : 403 en lecture, modification, publication")
    void autreEnseignant() throws Exception {
        String prof = "Bearer " + jeton("prof@ept.sn", "prof123");
        Integer id = creer(prof, "Privée");
        String autre = "Bearer " + jeton(nouvelEnseignant(), "motDePasse");

        mockMvc.perform(get("/enseignant/evaluations/" + id).header("Authorization", autre)).andExpect(status().isForbidden());
        mockMvc.perform(post("/enseignant/evaluations/" + id + "/publier").header("Authorization", autre)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/enseignant/evaluations/" + id).header("Authorization", autre)).andExpect(status().isForbidden());
        mockMvc.perform(get("/enseignant/evaluations").header("Authorization", autre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Publication d'un brouillon sans question : 400")
    void publierSansQuestion() throws Exception {
        String prof = "Bearer " + jeton("prof@ept.sn", "prof123");
        String sansQuestion = corps(promotionId(), "Vide").replaceAll("\"questions\":\\[.*\\]\\}$", "\"questions\":[]}");
        String reponse = mockMvc.perform(post("/enseignant/evaluations").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content(sansQuestion))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(reponse, "$.id");

        mockMvc.perform(post("/enseignant/evaluations/" + id + "/publier").header("Authorization", prof))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Aménagement : défini sur une évaluation publiée, durée trop courte refusée, suppression")
    void amenagement() throws Exception {
        String prof = "Bearer " + jeton("prof@ept.sn", "prof123");
        Integer id = creer(prof, "Tiers-temps");
        Long etudiantId = nouvelEtudiantId();

        mockMvc.perform(put("/enseignant/evaluations/" + id + "/amenagements/" + etudiantId).header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dureeMinutes\":30}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/enseignant/evaluations/" + id + "/amenagements/" + etudiantId).header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dureeMinutes\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dureeMinutes").value(80));

        mockMvc.perform(get("/enseignant/evaluations/" + id).header("Authorization", prof))
                .andExpect(jsonPath("$.amenagements[0].dureeMinutes").value(80));

        mockMvc.perform(delete("/enseignant/evaluations/" + id + "/amenagements/" + etudiantId).header("Authorization", prof))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/enseignant/evaluations/" + id + "/amenagements/" + etudiantId).header("Authorization", prof))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Détail étudiant : 404 pour un brouillon, 200 une fois publiée (sans aucune bonne réponse)")
    void detailEtudiant() throws Exception {
        String prof = "Bearer " + jeton("prof@ept.sn", "prof123");
        Integer id = creer(prof, "Visible");
        String etudiant = "Bearer " + jeton(nouvelEtudiantDic2(), "motDePasse");

        mockMvc.perform(get("/etudiant/evaluations/" + id).header("Authorization", etudiant)).andExpect(status().isNotFound());

        mockMvc.perform(post("/enseignant/evaluations/" + id + "/publier").header("Authorization", prof)).andExpect(status().isOk());

        mockMvc.perform(get("/etudiant/evaluations/" + id).header("Authorization", etudiant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Visible"))
                .andExpect(jsonPath("$.dureeMinutes").value(60))
                .andExpect(jsonPath("$.nbQuestions").value(2))
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andExpect(jsonPath("$.estCorrect").doesNotExist());

        mockMvc.perform(get("/etudiant/evaluations/" + id).header("Authorization", prof)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- outils

    private Integer creer(String bearer, String titre) throws Exception {
        String reponse = mockMvc.perform(post("/enseignant/evaluations").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(corps(promotionId(), titre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private Long promotionId() {
        return promotionRepository.findByNom("DIC2 Informatique").map(Promotion::getId).orElseThrow();
    }

    /** Corps de création : deux questions (QCM et ouverte), ouverture demain, fermeture dans deux jours. */
    private String corps(Long promotionId, String titre) {
        LocalDateTime ouverture = LocalDateTime.now().plusDays(1).withNano(0);
        return "{\"titre\":\"" + titre + "\",\"description\":\"d\",\"dureeStandard\":60,"
                + "\"dateOuverture\":\"" + ouverture + "\",\"dateFermeture\":\"" + ouverture.plusDays(1) + "\","
                + "\"promotionIds\":[" + promotionId + "],\"questions\":["
                + "{\"type\":\"QCM\",\"enonce\":\"Protocole fiable ?\",\"points\":2,\"estChoixMultiple\":false,"
                + "\"choix\":[{\"texte\":\"TCP\",\"estCorrect\":true},{\"texte\":\"UDP\",\"estCorrect\":false}]},"
                + "{\"type\":\"OUVERTE\",\"enonce\":\"Expliquez le routage\",\"points\":4}]}";
    }

    private String nouvelEtudiantDic2() {
        String email = "etu-eval-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Eval", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private Long nouvelEtudiantId() {
        return utilisateurRepository.findByEmail(nouvelEtudiantDic2()).orElseThrow().getId();
    }

    private String nouvelEnseignant() {
        String email = "prof-eval-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Enseignant("Fall", "Awa", email, passwordEncoder.encode("motDePasse"),
                "ENS-" + UUID.randomUUID()));
        return email;
    }

    private String jeton(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.token");
    }
}
