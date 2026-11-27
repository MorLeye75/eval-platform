package sn.ept.eval.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.QuestionOuverte;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Liste des étudiants et de leurs tiers-temps (DS-06) de bout en bout sur H2. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:amenagements-liste;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class AmenagementsListeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SoumissionService soumissionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("Liste : durée standard, plage, tiers-temps de chacun ; modifiable seulement sans copie ; trié par nom")
    void liste() throws Exception {
        Long id = evaluation(StatutEvaluation.EN_COURS);
        String sansCopie = nouvelEtudiant("Alpha");
        String avecCopie = nouvelEtudiant("Bravo");
        soumissionService.demarrerOuReprendre(id, avecCopie);
        String prof = bearer("prof@ept.sn", "prof123");

        Long idAlpha = etudiantId(id, "Alpha", prof);
        mockMvc.perform(put("/enseignant/evaluations/" + id + "/amenagements/" + idAlpha).header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dureeMinutes\":90}"))
                .andExpect(status().isOk());

        String corps = mockMvc.perform(get("/enseignant/evaluations/" + id + "/amenagements").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(id))
                .andExpect(jsonPath("$.dureeStandard").value(60))
                .andExpect(jsonPath("$.plageMinutes").value(150))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<Integer>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Alpha')].dureeMinutes")).containsExactly(90);
        assertThat((List<Boolean>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Alpha')].modifiable")).containsExactly(true);
        // Bravo a commencé : verrouillé, sans aménagement (durée absente, jamais 0)
        assertThat((List<Boolean>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Bravo')].modifiable")).containsExactly(false);
        assertThat((List<Object>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Bravo')].dureeMinutes")).containsExactly((Object) null);
        List<String> noms = JsonPath.read(corps, "$.etudiants[*].nom");
        assertThat(noms).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(sansCopie).isNotNull();
    }

    @Test
    @DisplayName("Évaluation terminée : plus rien n'est modifiable, la liste reste consultable")
    void terminee() throws Exception {
        Long id = evaluation(StatutEvaluation.TERMINEE);
        nouvelEtudiant("Charlie");
        String corps = mockMvc.perform(get("/enseignant/evaluations/" + id + "/amenagements")
                        .header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<Boolean>) JsonPath.read(corps, "$.etudiants[*].modifiable")).isNotEmpty().containsOnly(false);
    }

    @Test
    @DisplayName("Accès : autre enseignant 403, étudiant 403, sans jeton 401, évaluation inconnue 404")
    void acces() throws Exception {
        Long id = evaluation(StatutEvaluation.PUBLIEE);
        mockMvc.perform(get("/enseignant/evaluations/" + id + "/amenagements").header("Authorization", bearer(nouvelEnseignant(), "motDePasse")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/enseignant/evaluations/" + id + "/amenagements").header("Authorization", bearer("etudiant@ept.sn", "etudiant123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/enseignant/evaluations/" + id + "/amenagements")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/enseignant/evaluations/999999/amenagements").header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- outils

    private Long etudiantId(Long evaluationId, String nom, String prof) throws Exception {
        String corps = mockMvc.perform(get("/enseignant/evaluations/" + evaluationId + "/amenagements").header("Authorization", prof))
                .andReturn().getResponse().getContentAsString();
        return ((Number) ((List<?>) JsonPath.read(corps, "$.etudiants[?(@.nom=='" + nom + "')].etudiantId")).get(0)).longValue();
    }

    private Long evaluation(StatutEvaluation statut) {
        return transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Tiers-temps " + UUID.randomUUID());
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusMinutes(30));
            e.setDateFermeture(LocalDateTime.now().plusMinutes(120));
            e.setStatut(statut);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            e.addQuestion(new QuestionOuverte("Question un", 1, 5.0));
            return evaluationRepository.save(e).getId();
        });
    }

    private String nouvelEtudiant(String nom) {
        String email = "etu-ame-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant(nom, "Test", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private String nouvelEnseignant() {
        String email = "prof-ame-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Enseignant("Fall", "Awa", email, passwordEncoder.encode("motDePasse"), "ENS-" + UUID.randomUUID()));
        return email;
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }
}
