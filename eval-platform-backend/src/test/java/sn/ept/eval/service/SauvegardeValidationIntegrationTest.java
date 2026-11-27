package sn.ept.eval.service;

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
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sauvegarde (DS-02) : une requête mal formée est refusée en 400, avant tout traitement. Sans cela, un élément nul
 * dans la liste faisait une erreur 500, et une réponse sans questionId était ignorée en silence (200, version
 * incrémentée), le navigateur la croyant enregistrée. Base H2 propre à cette classe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:validation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class SauvegardeValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String auth;
    private Long soumissionId;

    /** Copie en cours d'un nouvel étudiant sur une évaluation QCM ouverte. */
    @BeforeEach
    void setUp() throws Exception {
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        Long evaluationId = transactionTemplate.execute(tx -> {
            Evaluation e = new Evaluation();
            e.setTitre("Validation " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(1));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            e.setPromotions(new HashSet<>(Set.of(dic2)));
            QuestionQCM q = new QuestionQCM("2 + 2 ?", 1, 1.0, false);
            q.addChoix(new ChoixReponse("4", true));
            q.addChoix(new ChoixReponse("5", false));
            e.addQuestion(q);
            return evaluationRepository.save(e).getId();
        });
        String email = "etu-val-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Validation", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), dic2));
        soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();

        String login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"motDePasse\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        auth = "Bearer " + JsonPath.read(login, "$.token");
    }

    private void sauvegarderEtAttendre400(String corps) throws Exception {
        mockMvc.perform(put("/soumissions/{id}/sauvegarder", soumissionId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
        // Refus avant tout traitement : la version des réponses n'a pas bougé
        assertThat(soumissionRepository.findById(soumissionId).orElseThrow().getVersionReponses()).isZero();
    }

    @Test
    @DisplayName("Élément nul dans la liste des réponses : 400, pas d'erreur 500")
    void elementNul_400() throws Exception {
        sauvegarderEtAttendre400("{\"reponses\":[null]}");
    }

    @Test
    @DisplayName("Réponse sans questionId : 400, plus ignorée en silence")
    void sansQuestionId_400() throws Exception {
        sauvegarderEtAttendre400("{\"reponses\":[{\"texteSoumis\":\"ma réponse\"}]}");
    }

    @Test
    @DisplayName("Liste des réponses absente (null) : 400")
    void listeNulle_400() throws Exception {
        sauvegarderEtAttendre400("{\"reponses\":null}");
    }
}
