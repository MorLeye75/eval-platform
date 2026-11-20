package sn.ept.eval.service;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;
import sn.ept.eval.service.ia.CorrecteurIA;
import sn.ept.eval.service.ia.CorrecteurIA.CorrecteurIAIndisponibleException;
import sn.ept.eval.service.ia.CorrecteurIA.Proposition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Correction assistée par IA et validation (DS-08) de bout en bout sur H2, connecteur IA et notifications simulés. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:correction-ia;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class CorrectionIAIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SoumissionService soumissionService;
    @Autowired private CorrectionIAService correctionIAService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private NotificationService notificationService;
    @MockitoBean private CorrecteurIA correcteur;

    @BeforeEach
    void modeleParDefaut() {
        reset(correcteur, notificationService);
        when(correcteur.proposer(anyString(), anyDouble(), anyString())).thenReturn(new Proposition(3.0, "Bonne réponse."));
    }

    /** Une évaluation terminée (questions de 4 et 2 points) et une copie EN_CORRECTION avec ses deux réponses. */
    private record Cas(Long evaluationId, Long copieId, List<Long> reponseIds) {}

    @Test
    @DisplayName("Parcours : générer, valider la 1re avec une note, valider la 2e sans note (note IA bornée au barème de 2) -> copie EVALUE, notée, étudiant notifié une fois")
    void parcoursComplet() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, true);
        String prof = bearer("prof@ept.sn", "prof123");

        mockMvc.perform(post("/enseignant/evaluations/" + cas.evaluationId() + "/corrections-ia/generer").header("Authorization", prof))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nbReponsesAProposer").value(2));
        attendreLesPropositions(prof, cas.evaluationId(), 2);

        String liste = mockMvc.perform(get("/enseignant/corrections-ia?evaluationId=" + cas.evaluationId() + "&statut=EN_ATTENTE")
                        .header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCours").value(0))
                .andExpect(jsonPath("$.propositions[0].noteProposee").value(3.0))
                .andExpect(jsonPath("$.propositions[0].reponse").exists())
                .andReturn().getResponse().getContentAsString();
        int premiere = JsonPath.read(liste, "$.propositions[0].id");
        int deuxieme = JsonPath.read(liste, "$.propositions[1].id");

        mockMvc.perform(put("/enseignant/corrections-ia/" + premiere + "/valider").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":3.5,\"commentaire\":\"Bien\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteRetenue").value(3.5))
                .andExpect(jsonPath("$.copieEvaluee").value(false));
        verify(notificationService, never()).resultatPublie(any());

        mockMvc.perform(put("/enseignant/corrections-ia/" + deuxieme + "/valider").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteRetenue").value(2.0))
                .andExpect(jsonPath("$.copieEvaluee").value(true))
                .andExpect(jsonPath("$.noteFinale").value(5.5));

        Soumission copie = soumissionRepository.findById(cas.copieId()).orElseThrow();
        assertThat(copie.getStatut().name()).isEqualTo("EVALUE");
        assertThat(copie.getNoteFinale()).isEqualTo(5.5);
        verify(notificationService, times(1)).resultatPublie(any());
    }

    @Test
    @DisplayName("Résultats pas encore publiés : la copie passe EVALUE, resultatPublie est appelé (il ne fait rien tant que rien n'est publié)")
    void notificationAppeleeALaDerniereNote() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        String prof = bearer("prof@ept.sn", "prof123");
        correctionIAService.proposer(cas.reponseIds().get(0));
        correctionIAService.proposer(cas.reponseIds().get(1));

        mockMvc.perform(put("/enseignant/reponses/" + cas.reponseIds().get(0) + "/note").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":1}"))
                .andExpect(status().isOk());
        // Une réponse reste à noter : la copie est encore en correction, personne n'est notifié
        assertThat(statutEtNoteDeLaCopie(cas.copieId())).containsExactly("EN_CORRECTION", null);
        verify(notificationService, never()).resultatPublie(any());

        mockMvc.perform(put("/enseignant/reponses/" + cas.reponseIds().get(1) + "/note").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":1}"))
                .andExpect(status().isOk());
        assertThat(statutEtNoteDeLaCopie(cas.copieId())).containsExactly("EVALUE", 2.0);
        verify(notificationService, times(1)).resultatPublie(any());
    }

    @Test
    @DisplayName("Rejeter : note obligatoire (400), hors barème (400), puis rejet noté ; deuxième décision refusée (409)")
    void rejeterEtDecisionsRefusees() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        String prof = bearer("prof@ept.sn", "prof123");
        correctionIAService.proposer(cas.reponseIds().get(0));
        Long propositionId = propositionDe(cas.reponseIds().get(0));

        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/rejeter").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"non\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/rejeter").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":4.5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/valider").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":-1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/rejeter").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":1.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteRetenue").value(1.25));
        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/valider").header("Authorization", prof))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CORRECTION_NON_MODIFIABLE"));

        String statut = jdbcTemplate.queryForObject("select statut from propositions_ia where id = ?", String.class, propositionId);
        assertThat(statut).isEqualTo("REJETEE");
    }

    @Test
    @DisplayName("Accès : autre enseignant 403, étudiant 403, sans jeton 401, proposition inconnue 404")
    void acces() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        correctionIAService.proposer(cas.reponseIds().get(0));
        Long propositionId = propositionDe(cas.reponseIds().get(0));

        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/valider")
                        .header("Authorization", bearer(nouvelEnseignant(), "motDePasse")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/valider")
                        .header("Authorization", bearer(nouvelEtudiant(), "motDePasse")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/valider")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/enseignant/corrections-ia/999999/valider").header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/enseignant/evaluations/" + cas.evaluationId() + "/corrections-ia/generer")
                        .header("Authorization", bearer(nouvelEnseignant(), "motDePasse")))
                .andExpect(status().isForbidden());
        assertThat(soumissionRepository.findById(cas.copieId()).orElseThrow().getStatut().name()).isEqualTo("EN_CORRECTION");
    }

    @Test
    @DisplayName("Générer refusé tant que l'évaluation n'est pas terminée (409)")
    void genererExigeUneEvaluationTerminee() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.EN_COURS, false);
        mockMvc.perform(post("/enseignant/evaluations/" + cas.evaluationId() + "/corrections-ia/generer")
                        .header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NON_MODIFIABLE"));
    }

    @Test
    @DisplayName("Note du modèle hors barème : ramenée dans [0 ; barème] et signalée ; réponse vide : 0 sans appel au modèle")
    void noteBorneeEtReponseVide() {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        when(correcteur.proposer(anyString(), anyDouble(), anyString())).thenReturn(new Proposition(99.0, "Parfait"));
        jdbcTemplate.update("update reponses_ouvertes set texte_soumis = '   ' where id = ?", cas.reponseIds().get(1));

        correctionIAService.proposer(cas.reponseIds().get(0));
        correctionIAService.proposer(cas.reponseIds().get(1));

        PropositionIA bornee = proposition(cas.reponseIds().get(0));
        assertThat(bornee.getNoteProposee()).isEqualTo(4.0);
        assertThat(bornee.getJustification()).startsWith("Note corrigée");
        PropositionIA vide = proposition(cas.reponseIds().get(1));
        assertThat(vide.getNoteProposee()).isZero();
        verify(correcteur, times(1)).proposer(anyString(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("Modèle indisponible : aucune proposition, rien ne casse ; la note manuelle prend le relais")
    void modeleIndisponible_noteManuelle() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        when(correcteur.proposer(anyString(), anyDouble(), anyString()))
                .thenThrow(new CorrecteurIAIndisponibleException("délai dépassé", null));
        String prof = bearer("prof@ept.sn", "prof123");

        correctionIAService.proposer(cas.reponseIds().get(0));
        assertThat(propositionOuNull(cas.reponseIds().get(0))).isNull();
        mockMvc.perform(get("/enseignant/corrections-ia?evaluationId=" + cas.evaluationId()).header("Authorization", prof))
                .andExpect(jsonPath("$.propositions").isEmpty())
                .andExpect(jsonPath("$.enCours").value(2));
        // La liste à noter à la main contient exactement ces deux réponses (même critère que le compteur « enCours »)
        mockMvc.perform(get("/enseignant/corrections-manuelles?evaluationId=" + cas.evaluationId()).header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reponseId").value(cas.reponseIds().get(0)))
                .andExpect(jsonPath("$[0].pointsMax").value(4.0))
                .andExpect(jsonPath("$[0].etudiant").isString());

        mockMvc.perform(put("/enseignant/reponses/" + cas.reponseIds().get(0) + "/note").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":2}"))
                .andExpect(status().isOk());
        // Une réponse notée quitte la liste
        mockMvc.perform(get("/enseignant/corrections-manuelles?evaluationId=" + cas.evaluationId()).header("Authorization", prof))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reponseId").value(cas.reponseIds().get(1)));
        mockMvc.perform(get("/enseignant/corrections-manuelles").header("Authorization", bearer("etudiant@ept.sn", "etudiant123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/enseignant/corrections-manuelles")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/enseignant/reponses/" + cas.reponseIds().get(0) + "/note").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":3}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/enseignant/reponses/" + cas.reponseIds().get(1) + "/note").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deux générations successives : une seule proposition par réponse (idempotence)")
    void proposerDeuxFois() {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        Long reponseId = cas.reponseIds().get(0);
        int avant = jdbcTemplate.queryForObject("select count(*) from propositions_ia", Integer.class);

        correctionIAService.proposer(reponseId);
        Long premiere = propositionDe(reponseId);
        correctionIAService.proposer(reponseId);

        verify(correcteur, times(1)).proposer(anyString(), anyDouble(), anyString());
        assertThat(premiere).isNotNull();
        assertThat(propositionDe(reponseId)).isEqualTo(premiere); // la proposition n'a pas été remplacée
        // une seule ligne créée par les deux appels (les autres tests de la classe partagent la base : on compare à l'état d'avant)
        assertThat(jdbcTemplate.queryForObject("select count(*) from propositions_ia", Integer.class)).isEqualTo(avant + 1);
    }

    @Test
    @DisplayName("Course : la note manuelle est posée pendant que l'IA écrit sa proposition -> la proposition tardive n'est pas enregistrée et la note reste")
    void propositionTardiveNeConcurrenceCasUneNoteManuelle() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        Long reponseId = cas.reponseIds().get(0);
        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch iaLancee = new CountDownLatch(1);
        ExecutorService fils = Executors.newFixedThreadPool(2);
        try {
            // Fil « enseignant » : tient le verrou de la copie, laisse l'IA arriver à l'écriture, puis pose la note et valide
            Future<?> enseignant = fils.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                soumissionRepository.findByIdForUpdate(cas.copieId());
                verrouPris.countDown();
                try {
                    iaLancee.await(5, TimeUnit.SECONDS);
                    Thread.sleep(400); // le temps que l'IA atteigne son écriture
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                jdbcTemplate.update("update reponses_etudiant set note_obtenue = 3, validee_par_prof = true where id = ?", reponseId);
            }));
            assertThat(verrouPris.await(5, TimeUnit.SECONDS)).isTrue();
            // Fil « IA » : lit la réponse (encore à noter), répond, puis veut écrire alors que la copie est verrouillée
            Future<?> ia = fils.submit(() -> correctionIAService.proposer(reponseId));
            iaLancee.countDown();
            enseignant.get(10, TimeUnit.SECONDS);
            ia.get(10, TimeUnit.SECONDS);
        } finally {
            fils.shutdownNow();
        }

        assertThat(propositionDe(reponseId)).as("aucune proposition ne doit apparaître sur une réponse déjà notée").isNull();
        assertThat(jdbcTemplate.queryForObject("select note_obtenue from reponses_etudiant where id = ?", Double.class, reponseId)).isEqualTo(3.0);
        assertThat(jdbcTemplate.queryForObject("select validee_par_prof from reponses_etudiant where id = ?", Boolean.class, reponseId)).isTrue();
    }

    @Test
    @DisplayName("Proposition périmée (réponse notée à la main entre-temps) : la valider est refusée (409) et ne remplace pas la note")
    void validerUnePropositionPerimeeEstRefuse() throws Exception {
        Cas cas = copieAvecDeuxReponses(StatutEvaluation.TERMINEE, false);
        Long reponseId = cas.reponseIds().get(0);
        String prof = bearer("prof@ept.sn", "prof123");
        correctionIAService.proposer(reponseId);
        Long propositionId = propositionDe(reponseId);
        assertThat(propositionId).isNotNull();
        // état que la course laissait : proposition en attente sur une réponse déjà notée
        jdbcTemplate.update("update reponses_etudiant set note_obtenue = 3, validee_par_prof = true where id = ?", reponseId);

        mockMvc.perform(put("/enseignant/corrections-ia/" + propositionId + "/valider").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("select note_obtenue from reponses_etudiant where id = ?", Double.class, reponseId)).isEqualTo(3.0);
    }

    // ---------------------------------------------------------------- outils

    private void attendreLesPropositions(String prof, Long evaluationId, int attendues) throws Exception {
        for (int i = 0; i < 100; i++) {
            String corps = mockMvc.perform(get("/enseignant/corrections-ia?evaluationId=" + evaluationId).header("Authorization", prof))
                    .andReturn().getResponse().getContentAsString();
            List<?> propositions = JsonPath.read(corps, "$.propositions");
            if (propositions.size() >= attendues) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Propositions non générées à temps");
    }

    /** {statut, noteFinale} d'une copie, lus en base. */
    private List<Object> statutEtNoteDeLaCopie(Long copieId) {
        return jdbcTemplate.queryForObject("select statut, note_finale from soumissions where id = ?",
                (rs, i) -> java.util.Arrays.<Object>asList(rs.getString(1), (Double) rs.getObject(2)), copieId);
    }

    private Long propositionDe(Long reponseId) {
        return jdbcTemplate.queryForObject("select proposition_ia_id from reponses_ouvertes where id = ?", Long.class, reponseId);
    }

    private PropositionIA proposition(Long reponseId) {
        PropositionIA p = propositionOuNull(reponseId);
        assertThat(p).isNotNull();
        return p;
    }

    private PropositionIA propositionOuNull(Long reponseId) {
        return transactionTemplate.execute(tx -> {
            Long id = propositionDe(reponseId);
            if (id == null) return null;
            PropositionIA p = new PropositionIA();
            jdbcTemplate.query("select note_proposee, justification, statut from propositions_ia where id = ?", rs -> {
                p.setNoteProposee(rs.getDouble(1));
                p.setJustification(rs.getString(2));
            }, id);
            return p;
        });
    }

    private Cas copieAvecDeuxReponses(StatutEvaluation statut, boolean resultatsPublies) {
        Long evaluationId = transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Correction IA " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(3));
            e.setDateFermeture(LocalDateTime.now().minusMinutes(30));
            e.setStatut(statut);
            e.setResultatsPublies(resultatsPublies);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            e.addQuestion(new QuestionOuverte("Expliquez le protocole TCP en détail", 1, 4.0));
            e.addQuestion(new QuestionOuverte("Citez deux avantages du découplage", 2, 2.0));
            return evaluationRepository.save(e).getId();
        });
        String etudiant = nouvelEtudiant();
        Object[] ancien = jdbcTemplate.queryForObject(
                "select statut, date_ouverture, date_fermeture from evaluations where id = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getTimestamp(2), rs.getTimestamp(3)}, evaluationId);
        jdbcTemplate.update("update evaluations set statut = 'EN_COURS', date_ouverture = ?, date_fermeture = ? where id = ?",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), evaluationId);
        Long copieId = soumissionService.demarrerOuReprendre(evaluationId, etudiant).getId();
        jdbcTemplate.update("update evaluations set statut = ?, date_ouverture = ?, date_fermeture = ? where id = ?",
                ancien[0], ancien[1], ancien[2], evaluationId);

        List<Long> reponseIds = transactionTemplate.execute(tx -> {
            Soumission copie = soumissionRepository.findById(copieId).orElseThrow();
            List<Question> questions = new ArrayList<>(copie.getEvaluation().getQuestions());
            questions.sort(java.util.Comparator.comparing(Question::getOrdre));
            List<Long> ids = new ArrayList<>();
            for (Question q : questions) {
                ReponseOuverte r = new ReponseOuverte();
                r.setQuestion(q);
                r.setTexteSoumis("Réponse de l'étudiant à : " + q.getEnonce());
                copie.addReponse(r);
            }
            copie.setStatut(sn.ept.eval.model.enums.StatutSoumission.EN_CORRECTION);
            soumissionRepository.saveAndFlush(copie);
            copie.getReponses().forEach(r -> ids.add(r.getId()));
            return ids;
        });
        return new Cas(evaluationId, copieId, reponseIds);
    }

    private String nouvelEtudiant() {
        String email = "etu-ia-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Correction", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private String nouvelEnseignant() {
        String email = "prof-ia-" + UUID.randomUUID() + "@ept.sn";
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
