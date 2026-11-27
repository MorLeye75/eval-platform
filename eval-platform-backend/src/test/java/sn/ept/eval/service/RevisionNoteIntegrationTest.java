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
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
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

/**
 * Copie vue par l'enseignant et révision d'une note avant la publication des résultats, avec historique (ancienne note,
 * nouvelle note, motif, auteur, date). Après publication, plus aucune note ne change.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:revision-note;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class RevisionNoteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private NotificationService notificationService;

    /** Copie d'une question de code (4 points, 2 cas de test) et d'une question ouverte (2 points). */
    private record Copie(Long evaluationId, Long copieId, Long reponseCodeId, Long reponseOuverteId) {}

    @Test
    @DisplayName("Avant publication : la note de code posée par Judge0 (2/4) est révisée à 4 avec un motif -> note finale recalculée, historique gardé, motif visible par l'étudiant")
    void reviserUneNoteDeJudge0AvantPublication() throws Exception {
        Copie c = copie(false, true);
        String prof = bearer("prof@ept.sn", "prof123");

        // La copie complète : code, verdicts des cas de test, notes
        mockMvc.perform(get("/enseignant/soumissions/" + c.copieId() + "/copie").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EVALUE"))
                .andExpect(jsonPath("$.noteFinale").value(3.5))
                .andExpect(jsonPath("$.bareme").value(6.0))
                .andExpect(jsonPath("$.notesModifiables").value(true))
                .andExpect(jsonPath("$.reponses[0].type").value("CODE"))
                .andExpect(jsonPath("$.reponses[0].code").value("print(input().strip() )"))
                .andExpect(jsonPath("$.reponses[0].langage").value("python"))
                .andExpect(jsonPath("$.reponses[0].note").value(2.0))
                .andExpect(jsonPath("$.reponses[0].notee").value(true))
                .andExpect(jsonPath("$.reponses[0].casDeTests.length()").value(2))
                .andExpect(jsonPath("$.reponses[0].casDeTests[1].verdict").value("MAUVAISE_REPONSE"))
                .andExpect(jsonPath("$.reponses[0].casDeTests[1].sortieAttendue").value("b"))
                .andExpect(jsonPath("$.reponses[0].casDeTests[1].sortieObtenue").value("b "))
                .andExpect(jsonPath("$.reponses[0].revisions.length()").value(0))
                .andExpect(jsonPath("$.reponses[1].type").value("OUVERTE"))
                .andExpect(jsonPath("$.reponses[1].texte").value("TCP garantit l'ordre."));

        mockMvc.perform(put("/enseignant/reponses/" + c.reponseCodeId() + "/revision").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":4,\"motif\":\"Sortie juste : seul un espace final diffère.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ancienneNote").value(2.0))
                .andExpect(jsonPath("$.nouvelleNote").value(4.0))
                .andExpect(jsonPath("$.noteFinale").value(5.5));

        Soumission apres = soumissionRepository.findById(c.copieId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutSoumission.EVALUE);
        assertThat(apres.getNoteFinale()).isEqualTo(5.5);

        mockMvc.perform(get("/enseignant/soumissions/" + c.copieId() + "/copie").header("Authorization", prof))
                .andExpect(jsonPath("$.noteFinale").value(5.5))
                .andExpect(jsonPath("$.reponses[0].note").value(4.0))
                .andExpect(jsonPath("$.reponses[0].commentaire").value("Note revue par l'enseignant : Sortie juste : seul un espace final diffère."))
                .andExpect(jsonPath("$.reponses[0].revisions.length()").value(1))
                .andExpect(jsonPath("$.reponses[0].revisions[0].ancienneNote").value(2.0))
                .andExpect(jsonPath("$.reponses[0].revisions[0].nouvelleNote").value(4.0))
                .andExpect(jsonPath("$.reponses[0].revisions[0].motif").value("Sortie juste : seul un espace final diffère."))
                .andExpect(jsonPath("$.reponses[0].revisions[0].auteur").exists())
                .andExpect(jsonPath("$.reponses[0].revisions[0].reviseeLe").exists());
    }

    @Test
    @DisplayName("Révision refusée (400) sans motif, hors barème, négative ou identique : rien ne change, rien n'est tracé")
    void revisionsInvalides() throws Exception {
        Copie c = copie(false, true);
        String prof = bearer("prof@ept.sn", "prof123");
        String url = "/enseignant/reponses/" + c.reponseCodeId() + "/revision";

        for (String corps : List.of("{\"note\":3}", "{\"note\":3,\"motif\":\"   \"}", "{\"motif\":\"Pas de note\"}",
                "{\"note\":4.5,\"motif\":\"Au-dessus du barème\"}", "{\"note\":-1,\"motif\":\"Négative\"}",
                "{\"note\":2,\"motif\":\"Même note\"}")) {
            mockMvc.perform(put(url).header("Authorization", prof).contentType(MediaType.APPLICATION_JSON).content(corps))
                    .andExpect(status().isBadRequest());
        }
        assertThat(noteDe(c.reponseCodeId())).isEqualTo(2.0);
        assertThat(nbRevisions(c.reponseCodeId())).isZero();
    }

    @Test
    @DisplayName("Résultats publiés : la note n'est plus modifiable (409), la copie l'indique, aucun historique")
    void apresPublicationLaNoteEstDefinitive() throws Exception {
        Copie c = copie(true, true);
        String prof = bearer("prof@ept.sn", "prof123");

        mockMvc.perform(get("/enseignant/soumissions/" + c.copieId() + "/copie").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultatsPublies").value(true))
                .andExpect(jsonPath("$.notesModifiables").value(false));
        mockMvc.perform(put("/enseignant/reponses/" + c.reponseCodeId() + "/revision").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":4,\"motif\":\"Trop tard\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CORRECTION_NON_MODIFIABLE"));

        assertThat(noteDe(c.reponseCodeId())).isEqualTo(2.0);
        assertThat(nbRevisions(c.reponseCodeId())).isZero();
    }

    @Test
    @DisplayName("Code en repli manuel (Judge0 indisponible) : visible non notée dans la copie, révision refusée (409), notée à la main -> copie EVALUE ; puis révisable")
    void codeEnRepliManuel() throws Exception {
        Copie c = copie(false, false);
        String prof = bearer("prof@ept.sn", "prof123");

        mockMvc.perform(get("/enseignant/soumissions/" + c.copieId() + "/copie").header("Authorization", prof))
                .andExpect(jsonPath("$.statut").value("EN_CORRECTION"))
                .andExpect(jsonPath("$.noteFinale").doesNotExist())
                .andExpect(jsonPath("$.reponses[0].notee").value(false))
                .andExpect(jsonPath("$.reponses[0].note").doesNotExist())
                .andExpect(jsonPath("$.reponses[0].code").value("print(input().strip() )"));

        mockMvc.perform(put("/enseignant/reponses/" + c.reponseCodeId() + "/revision").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":3,\"motif\":\"Pas encore notée\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/enseignant/reponses/" + c.reponseCodeId() + "/note").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copieEvaluee").value(true))
                .andExpect(jsonPath("$.noteFinale").value(4.5));

        mockMvc.perform(put("/enseignant/reponses/" + c.reponseOuverteId() + "/revision").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":2,\"motif\":\"Réponse complète à la relecture.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ancienneNote").value(1.5))
                .andExpect(jsonPath("$.noteFinale").value(5.0));
    }

    @Test
    @DisplayName("Une note révisée pendant la correction (copie EN_CORRECTION) : la note change, la note finale reste à venir")
    void revisionPendantLaCorrection() throws Exception {
        Copie c = copie(false, false);
        mockMvc.perform(put("/enseignant/reponses/" + c.reponseOuverteId() + "/revision")
                        .header("Authorization", bearer("prof@ept.sn", "prof123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":2,\"motif\":\"Relecture\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nouvelleNote").value(2.0))
                .andExpect(jsonPath("$.noteFinale").doesNotExist());
        assertThat(soumissionRepository.findById(c.copieId()).orElseThrow().getStatut()).isEqualTo(StatutSoumission.EN_CORRECTION);
        assertThat(nbRevisions(c.reponseOuverteId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Accès : un autre enseignant ou un étudiant ne voit ni ne révise la copie (403), sans jeton 401, inconnue 404")
    void acces() throws Exception {
        Copie c = copie(false, true);
        for (String jeton : List.of(bearer(nouvelEnseignant(), "motDePasse"), bearer(nouvelEtudiant(), "motDePasse"))) {
            mockMvc.perform(get("/enseignant/soumissions/" + c.copieId() + "/copie").header("Authorization", jeton))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/enseignant/reponses/" + c.reponseCodeId() + "/revision").header("Authorization", jeton)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"note\":4,\"motif\":\"Intrus\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/enseignant/soumissions/" + c.copieId() + "/copie")).andExpect(status().isUnauthorized());
        String prof = bearer("prof@ept.sn", "prof123");
        mockMvc.perform(get("/enseignant/soumissions/999999/copie").header("Authorization", prof)).andExpect(status().isNotFound());
        mockMvc.perform(put("/enseignant/reponses/999999/revision").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":1,\"motif\":\"Inconnue\"}"))
                .andExpect(status().isNotFound());
        assertThat(noteDe(c.reponseCodeId())).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Notes par étudiant : chaque ligne avec copie donne l'identifiant de la copie (lien vers la copie)")
    void lesLignesDeResultatsMenentALaCopie() throws Exception {
        Copie c = copie(false, true);
        String resultats = mockMvc.perform(get("/enseignant/evaluations/" + c.evaluationId() + "/resultats")
                        .header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(resultats, "$.lignes[?(@.soumissionId)].soumissionId");
        assertThat(ids).contains(c.copieId().intValue());
    }

    // ------------------------------------------------------------------ données

    /**
     * Évaluation terminée : code (4 pts, cas « a » réussi, cas « b » raté à un espace près) et ouverte (2 pts, notée 1,5).
     * {@code codeNote} faux : Judge0 est passé en repli manuel, le code reste à noter et la copie EN_CORRECTION.
     */
    private Copie copie(boolean resultatsPublies, boolean codeNote) {
        String email = nouvelEtudiant();
        Long[] ids = transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Révision " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(3));
            e.setDateFermeture(LocalDateTime.now().minusHours(1));
            e.setStatut(StatutEvaluation.TERMINEE);
            e.setResultatsPublies(resultatsPublies);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            QuestionCode code = new QuestionCode("Afficher l'entrée", 1, 4.0);
            code.addCasDeTest(new CasDeTest("a", "a", false));
            code.addCasDeTest(new CasDeTest("b", "b", true));
            e.addQuestion(code);
            e.addQuestion(new QuestionOuverte("Que garantit TCP ?", 2, 2.0));
            e = evaluationRepository.saveAndFlush(e);

            Soumission s = new Soumission();
            s.setEvaluation(e);
            s.setEtudiant((Etudiant) utilisateurRepository.findByEmail(email).orElseThrow());
            s.setDateRemise(LocalDateTime.now().minusHours(1).minusMinutes(5));

            ReponseCode rc = new ReponseCode();
            rc.setQuestion(code);
            rc.setCodeSoumis("print(input().strip() )");
            rc.setLangage("python");
            if (codeNote) {
                rc.addResultat(resultat(code.getCasTests().get(0), "a", true, VerdictCas.REUSSI));
                rc.addResultat(resultat(code.getCasTests().get(1), "b ", false, VerdictCas.MAUVAISE_REPONSE));
                rc.setNoteObtenue(2.0);
                rc.setValideeParProf(true);
                rc.setCommentaireProf("Correction automatique : 1/2 cas de test réussis.");
            }
            s.addReponse(rc);

            ReponseOuverte ro = new ReponseOuverte();
            ro.setQuestion(e.getQuestions().stream().filter(q -> q instanceof QuestionOuverte).findFirst().orElseThrow());
            ro.setTexteSoumis("TCP garantit l'ordre.");
            ro.setNoteObtenue(1.5);
            ro.setValideeParProf(true);
            s.addReponse(ro);

            s.setStatut(codeNote ? StatutSoumission.EVALUE : StatutSoumission.EN_CORRECTION);
            s.setNoteFinale(codeNote ? 3.5 : null);
            s = soumissionRepository.saveAndFlush(s);
            return new Long[]{e.getId(), s.getId(), rc.getId(), ro.getId()};
        });
        return new Copie(ids[0], ids[1], ids[2], ids[3]);
    }

    private static ResultatCasTest resultat(CasDeTest cas, String sortie, boolean reussi, VerdictCas verdict) {
        ResultatCasTest r = new ResultatCasTest();
        r.setCasDeTest(cas);
        r.setSortieObtenue(sortie);
        r.setEstValide(reussi);
        r.setTempsExecution(0.01);
        r.setVerdict(verdict);
        return r;
    }

    private Double noteDe(Long reponseId) {
        return jdbcTemplate.queryForObject("select note_obtenue from reponses_etudiant where id = ?", Double.class, reponseId);
    }

    private int nbRevisions(Long reponseId) {
        Integer n = jdbcTemplate.queryForObject("select count(*) from revisions_note where reponse_id = ?", Integer.class, reponseId);
        return n == null ? 0 : n;
    }

    private String nouvelEtudiant() {
        String email = "etu-rev-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Revision", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private String nouvelEnseignant() {
        String email = "prof-rev-" + UUID.randomUUID() + "@ept.sn";
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
