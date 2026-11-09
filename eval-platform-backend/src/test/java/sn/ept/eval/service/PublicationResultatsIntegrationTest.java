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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publication des résultats (DS-07) de bout en bout sur H2 : la route enseignant, ses refus, la notification et la
 * consultation côté étudiant (DS-13) qui passe de « EN_ATTENTE » à « PUBLIES ».
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:publication-resultats;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class PublicationResultatsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SoumissionService soumissionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** Remplace le service provisoire : on vérifie les appels sans dépendre du module de Mor. */
    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("Parcours complet : EN_ATTENTE avant, publication 200, PUBLIES après, notification envoyée une fois")
    void parcoursComplet() throws Exception {
        Long evaluationId = evaluationTerminee(StatutEvaluation.TERMINEE, LocalDateTime.now().minusMinutes(30));
        String etudiant = nouvelEtudiant();
        Long copieId = copieNotee(evaluationId, etudiant, 4.0);
        String prof = bearer("prof@ept.sn", "prof123");
        String bearerEtudiant = bearer(etudiant, "motDePasse");

        mockMvc.perform(get("/soumissions/" + copieId + "/resultats").header("Authorization", bearerEtudiant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE"));

        mockMvc.perform(post("/enseignant/evaluations/" + evaluationId + "/resultats/publier").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultatsPublies").value(true))
                .andExpect(jsonPath("$.nbCopies").value(1))
                .andExpect(jsonPath("$.nbCopiesNotees").value(1))
                .andExpect(jsonPath("$.nbCopiesEnAttente").value(0));

        mockMvc.perform(get("/soumissions/" + copieId + "/resultats").header("Authorization", bearerEtudiant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("PUBLIES"));

        verify(notificationService).resultatsPublies(argThat(e -> e.getId().equals(evaluationId)));
        assertThat(evaluationRepository.findById(evaluationId).orElseThrow().getResultatsPublies()).isTrue();
    }

    @Test
    @DisplayName("Double publication : le second appel renvoie 409 et ne notifie pas une seconde fois")
    void doublePublication() throws Exception {
        Long evaluationId = evaluationTerminee(StatutEvaluation.TERMINEE, LocalDateTime.now().minusMinutes(30));
        String prof = bearer("prof@ept.sn", "prof123");

        mockMvc.perform(post("/enseignant/evaluations/" + evaluationId + "/resultats/publier").header("Authorization", prof))
                .andExpect(status().isOk());
        mockMvc.perform(post("/enseignant/evaluations/" + evaluationId + "/resultats/publier").header("Authorization", prof))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NON_MODIFIABLE"));

        verify(notificationService, org.mockito.Mockito.times(1)).resultatsPublies(argThat(e -> e.getId().equals(evaluationId)));
    }

    @Test
    @DisplayName("Copie pas encore notée : la publication passe, la copie est comptée « en attente » et reste invisible")
    void copieEnAttente() throws Exception {
        Long evaluationId = evaluationTerminee(StatutEvaluation.TERMINEE, LocalDateTime.now().minusMinutes(30));
        String etudiant = nouvelEtudiant();
        Long copieId = nouvelleCopie(evaluationId, etudiant);
        jdbcTemplate.update("update soumissions set statut = 'EN_CORRECTION', date_remise = ? where id = ?",
                LocalDateTime.now().minusMinutes(40), copieId);
        String prof = bearer("prof@ept.sn", "prof123");

        mockMvc.perform(post("/enseignant/evaluations/" + evaluationId + "/resultats/publier").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCopiesNotees").value(0))
                .andExpect(jsonPath("$.nbCopiesEnAttente").value(1));

        mockMvc.perform(get("/soumissions/" + copieId + "/resultats").header("Authorization", bearer(etudiant, "motDePasse")))
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE"));
    }

    @Test
    @DisplayName("Refus : évaluation pas terminée, marge de tolérance non écoulée, autre enseignant, étudiant, sans jeton")
    void refus() throws Exception {
        String prof = bearer("prof@ept.sn", "prof123");

        Long enCours = evaluationTerminee(StatutEvaluation.EN_COURS, LocalDateTime.now().plusHours(1));
        mockMvc.perform(post("/enseignant/evaluations/" + enCours + "/resultats/publier").header("Authorization", prof))
                .andExpect(status().isConflict());

        Long tropTot = evaluationTerminee(StatutEvaluation.TERMINEE, LocalDateTime.now().minusSeconds(30));
        mockMvc.perform(post("/enseignant/evaluations/" + tropTot + "/resultats/publier").header("Authorization", prof))
                .andExpect(status().isConflict());

        Long terminee = evaluationTerminee(StatutEvaluation.TERMINEE, LocalDateTime.now().minusMinutes(30));
        mockMvc.perform(post("/enseignant/evaluations/" + terminee + "/resultats/publier")
                        .header("Authorization", bearer(nouvelEnseignant(), "motDePasse")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/enseignant/evaluations/" + terminee + "/resultats/publier")
                        .header("Authorization", bearer(nouvelEtudiant(), "motDePasse")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/enseignant/evaluations/" + terminee + "/resultats/publier"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/enseignant/evaluations/999999/resultats/publier").header("Authorization", prof))
                .andExpect(status().isNotFound());

        verify(notificationService, never()).resultatsPublies(any());
        assertThat(evaluationRepository.findById(terminee).orElseThrow().getResultatsPublies()).isFalse();
    }

    // ---------------------------------------------------------------- outils

    /** Évaluation du professeur de démonstration, avec une question ouverte, dans le statut et la fermeture voulus. */
    private Long evaluationTerminee(StatutEvaluation statut, LocalDateTime fermeture) {
        return transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Publication " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(fermeture.minusHours(2));
            e.setDateFermeture(fermeture);
            e.setStatut(statut);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            e.addQuestion(new QuestionOuverte("Expliquez TCP", 1, 4.0));
            return evaluationRepository.save(e).getId();
        });
    }

    /** Copie notée (statut EVALUE) : voir {@link #nouvelleCopie}. */
    private Long copieNotee(Long evaluationId, String etudiantEmail, double note) {
        Long copieId = nouvelleCopie(evaluationId, etudiantEmail);
        jdbcTemplate.update("update soumissions set statut = 'EVALUE', note_finale = ?, date_remise = ? where id = ?",
                note, LocalDateTime.now().minusMinutes(45), copieId);
        return copieId;
    }

    /**
     * Copie créée par la vraie passation, qui exige une évaluation ouverte : on l'ouvre le temps de créer la copie,
     * puis on remet l'évaluation dans son état de test.
     */
    private Long nouvelleCopie(Long evaluationId, String etudiantEmail) {
        Object[] ancien = jdbcTemplate.queryForObject(
                "select statut, date_ouverture, date_fermeture from evaluations where id = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getTimestamp(2), rs.getTimestamp(3)}, evaluationId);
        jdbcTemplate.update("update evaluations set statut = 'EN_COURS', date_ouverture = ?, date_fermeture = ? where id = ?",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), evaluationId);
        Long copieId = soumissionService.demarrerOuReprendre(evaluationId, etudiantEmail).getId();
        jdbcTemplate.update("update evaluations set statut = ?, date_ouverture = ?, date_fermeture = ? where id = ?",
                ancien[0], ancien[1], ancien[2], evaluationId);
        return copieId;
    }

    private String nouvelEtudiant() {
        String email = "etu-pub-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Publication", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private String nouvelEnseignant() {
        String email = "prof-pub-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Enseignant("Fall", "Awa", email, passwordEncoder.encode("motDePasse"),
                "ENS-" + UUID.randomUUID()));
        return email;
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }
}
