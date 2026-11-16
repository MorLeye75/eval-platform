package sn.ept.eval.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.AlerteAntiTricheRepository;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Supervision en lecture seule (DS-17) de bout en bout sur H2. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:supervision;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class SupervisionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SoumissionService soumissionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private AlerteAntiTricheRepository alerteRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("Avancement : états, réponses réellement données (une réponse vide ne compte pas), alertes triées, totaux")
    void avancement() throws Exception {
        Long id = evaluation(StatutEvaluation.EN_COURS);
        String a = nouvelEtudiant("Alpha"), b = nouvelEtudiant("Bravo");
        Long copieA = copie(id, a, StatutSoumission.EN_COURS, "oui", "non", "   ");   // 2 réponses données sur 3, 1 vide
        Long copieB = copie(id, b, StatutSoumission.SOUMIS, "a", "b", "c");           // 3 sur 3, remise
        jdbcTemplate.update("update soumissions set nb_changements_onglet = 4 where id = ?", copieA);
        alerte(copieA, "Sorties d'écran répétées (3)", NiveauAlerte.MODERE, LocalDateTime.now().minusMinutes(10));
        alerte(copieA, "Sorties d'écran répétées (5)", NiveauAlerte.ELEVE, LocalDateTime.now().minusMinutes(2));
        long attendus = jdbcTemplate.queryForObject(
                "select count(*) from etudiants e join promotions p on p.id = e.promotion_id where p.nom = 'DIC2 Informatique'", Long.class);

        String corps = mockMvc.perform(get("/enseignant/supervision/" + id).header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.nbQuestions").value(3))
                .andExpect(jsonPath("$.nbAttendus").value(attendus))
                .andExpect(jsonPath("$.nbCommences").value(2))
                .andExpect(jsonPath("$.nbSoumis").value(1))
                .andExpect(jsonPath("$.nbAlertes").value(2))
                // la plus récente d'abord
                .andExpect(jsonPath("$.alertes[0].niveau").value("ELEVE"))
                .andExpect(jsonPath("$.alertes[1].niveau").value("MODERE"))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<String>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Alpha')].etat")).containsExactly("EN_COURS");
        assertThat((List<Integer>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Alpha')].nbReponses")).containsExactly(2);
        assertThat((List<Integer>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Alpha')].nbChangementsOnglet")).containsExactly(4);
        assertThat((List<Integer>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Alpha')].nbAlertes")).containsExactly(2);
        assertThat((List<String>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Bravo')].etat")).containsExactly("SOUMIS");
        assertThat((List<Integer>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Bravo')].nbReponses")).containsExactly(3);
        // l'étudiant de démonstration n'a pas commencé
        assertThat((List<String>) JsonPath.read(corps, "$.etudiants[?(@.nom=='Niang')].etat")).containsExactly("NON_COMMENCE");
        // lecture seule, sans contenu : ni texte de réponse ni note
        assertThat(corps).doesNotContain("noteFinale").doesNotContain("texteSoumis").doesNotContain("\"oui\"");
        assertThat(copieB).isNotNull();
    }

    @Test
    @DisplayName("Une évaluation terminée reste consultable ; avant l'ouverture : 409")
    void statuts() throws Exception {
        String prof = bearer("prof@ept.sn", "prof123");
        mockMvc.perform(get("/enseignant/supervision/" + evaluation(StatutEvaluation.TERMINEE)).header("Authorization", prof))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TERMINEE"));
        for (StatutEvaluation avantOuverture : new StatutEvaluation[]{StatutEvaluation.BROUILLON, StatutEvaluation.PUBLIEE}) {
            mockMvc.perform(get("/enseignant/supervision/" + evaluation(avantOuverture)).header("Authorization", prof))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVALUATION_NON_MODIFIABLE"));
        }
    }

    @Test
    @DisplayName("Accès : autre enseignant 403, étudiant 403, sans jeton 401, évaluation inconnue 404")
    void acces() throws Exception {
        Long id = evaluation(StatutEvaluation.EN_COURS);
        mockMvc.perform(get("/enseignant/supervision/" + id).header("Authorization", bearer(nouvelEnseignant(), "motDePasse")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/enseignant/supervision/" + id).header("Authorization", bearer("etudiant@ept.sn", "etudiant123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/enseignant/supervision/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/enseignant/supervision/999999").header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- outils

    private Long evaluation(StatutEvaluation statut) {
        return transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Supervision " + UUID.randomUUID());
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusMinutes(30));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(statut);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            e.addQuestion(new QuestionOuverte("Question un", 1, 5.0));
            e.addQuestion(new QuestionOuverte("Question deux", 2, 5.0));
            e.addQuestion(new QuestionOuverte("Question trois", 3, 5.0));
            return evaluationRepository.save(e).getId();
        });
    }

    /** Copie créée par la vraie passation (évaluation EN_COURS ouverte), puis réponses posées. */
    private Long copie(Long evaluationId, String email, StatutSoumission statut, String... textes) {
        Long copieId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        transactionTemplate.executeWithoutResult(tx -> {
            Soumission copie = soumissionRepository.findById(copieId).orElseThrow();
            List<Question> questions = copie.getEvaluation().getQuestions().stream().sorted(Comparator.comparing(Question::getOrdre)).toList();
            for (int i = 0; i < questions.size(); i++) {
                ReponseOuverte r = new ReponseOuverte();
                r.setQuestion(questions.get(i));
                r.setTexteSoumis(textes[i]);
                copie.addReponse(r);
            }
            copie.setStatut(statut);
            soumissionRepository.saveAndFlush(copie);
        });
        return copieId;
    }

    private void alerte(Long copieId, String description, NiveauAlerte niveau, LocalDateTime date) {
        transactionTemplate.executeWithoutResult(tx -> {
            AlerteAntiTriche alerte = new AlerteAntiTriche(description, niveau, soumissionRepository.findById(copieId).orElseThrow());
            alerte.setDateDetection(date);
            alerteRepository.save(alerte);
        });
    }

    private String nouvelEtudiant(String nom) {
        String email = "etu-sup-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant(nom, "Test", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private String nouvelEnseignant() {
        String email = "prof-sup-" + UUID.randomUUID() + "@ept.sn";
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
