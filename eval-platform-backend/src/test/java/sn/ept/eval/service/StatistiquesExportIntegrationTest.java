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
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.nio.charset.StandardCharsets;
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

/** Statistiques et export CSV (DS-09) de bout en bout sur H2. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:statistiques-export;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class StatistiquesExportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SoumissionService soumissionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private NotificationService notificationService;

    /** Deux questions de 10 points (barème 20) ; notes sur 20 : 14 et 6 ; une copie en correction ; un étudiant absent. */
    private record Cas(Long evaluationId, List<String> emails) {}

    @Test
    @DisplayName("Statistiques : moyenne, médiane, min, max, écart-type, réussite, distribution, par question, effectifs")
    void statistiques() throws Exception {
        Cas cas = evaluationAvecResultats(StatutEvaluation.TERMINEE);
        String prof = bearer("prof@ept.sn", "prof123");
        long attendus = jdbcTemplate.queryForObject(
                "select count(*) from etudiants e join promotions p on p.id = e.promotion_id where p.nom = 'DIC2 Informatique'", Long.class);

        mockMvc.perform(get("/enseignant/evaluations/" + cas.evaluationId() + "/statistiques").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bareme").value(20.0))
                .andExpect(jsonPath("$.nbAttendus").value(attendus))
                .andExpect(jsonPath("$.nbCopies").value(3))
                .andExpect(jsonPath("$.nbNotees").value(2))
                .andExpect(jsonPath("$.nbEnCorrection").value(1))
                .andExpect(jsonPath("$.moyenneSur20").value(10.0))
                .andExpect(jsonPath("$.medianeSur20").value(10.0))
                .andExpect(jsonPath("$.minSur20").value(6.0))
                .andExpect(jsonPath("$.maxSur20").value(14.0))
                .andExpect(jsonPath("$.ecartTypeSur20").value(4.0))
                .andExpect(jsonPath("$.tauxReussite").value(50.0))
                .andExpect(jsonPath("$.distribution.length()").value(10))
                .andExpect(jsonPath("$.distribution[3].nb").value(1))
                .andExpect(jsonPath("$.distribution[7].nb").value(1))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].moyennePoints").value(6.0))
                .andExpect(jsonPath("$.questions[0].tauxReussite").value(60.0))
                .andExpect(jsonPath("$.questions[1].tauxReussite").value(40.0));
    }

    @Test
    @DisplayName("Aucune copie notée : indicateurs null (jamais 0), distribution à zéro, effectifs exacts")
    void sansCopieNotee() throws Exception {
        Long id = nouvelleEvaluation(StatutEvaluation.TERMINEE);

        mockMvc.perform(get("/enseignant/evaluations/" + id + "/statistiques").header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbNotees").value(0))
                .andExpect(jsonPath("$.moyenneSur20").doesNotExist())
                .andExpect(jsonPath("$.ecartTypeSur20").doesNotExist())
                .andExpect(jsonPath("$.tauxReussite").doesNotExist())
                .andExpect(jsonPath("$.questions[0].tauxReussite").doesNotExist())
                .andExpect(jsonPath("$.distribution[0].nb").value(0));
    }

    @Test
    @DisplayName("Accès : brouillon et publiée 409, autre enseignant 403, étudiant 403, sans jeton 401, inconnue 404")
    void acces() throws Exception {
        String prof = bearer("prof@ept.sn", "prof123");
        Long brouillon = nouvelleEvaluation(StatutEvaluation.BROUILLON);
        Long publiee = nouvelleEvaluation(StatutEvaluation.PUBLIEE);
        Long terminee = nouvelleEvaluation(StatutEvaluation.TERMINEE);

        for (String route : new String[]{"/statistiques", "/export/csv", "/export/detail-csv", "/export/pdf", "/resultats"}) {
            mockMvc.perform(get("/enseignant/evaluations/" + brouillon + route).header("Authorization", prof)).andExpect(status().isConflict());
            mockMvc.perform(get("/enseignant/evaluations/" + publiee + route).header("Authorization", prof)).andExpect(status().isConflict());
            mockMvc.perform(get("/enseignant/evaluations/" + terminee + route).header("Authorization", bearer(nouvelEnseignant(), "motDePasse")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/enseignant/evaluations/" + terminee + route).header("Authorization", bearer("etudiant@ept.sn", "etudiant123")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/enseignant/evaluations/" + terminee + route)).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/enseignant/evaluations/999999" + route).header("Authorization", prof)).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("Export CSV : en-têtes (attachment, no-store, UTF-8 BOM), une ligne par étudiant attendu, absent, copie en correction, notes, formule neutralisée")
    void exportCsv() throws Exception {
        Cas cas = evaluationAvecResultats(StatutEvaluation.TERMINEE);
        long attendus = jdbcTemplate.queryForObject(
                "select count(*) from etudiants e join promotions p on p.id = e.promotion_id where p.nom = 'DIC2 Informatique'", Long.class);

        var reponse = mockMvc.perform(get("/enseignant/evaluations/" + cas.evaluationId() + "/export/csv")
                        .header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"resultats-" + cas.evaluationId() + ".csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        assertThat(reponse.getContentType()).startsWith("text/csv");
        byte[] octets = reponse.getContentAsByteArray();
        assertThat(octets).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        String csv = new String(octets, 3, octets.length - 3, StandardCharsets.UTF_8);
        List<String> lignes = csv.lines().toList();
        assertThat(lignes.get(0)).isEqualTo(ExportResultatsService.ENTETE);
        assertThat(lignes).hasSize((int) attendus + 1);
        // notes avec virgule décimale, sur le barème, puis sur 20
        assertThat(lignes).anyMatch(l -> l.contains(cas.emails().get(0)) && l.contains(";EVALUE;14,0;20,0;14,0;"));
        assertThat(lignes).anyMatch(l -> l.contains(cas.emails().get(1)) && l.contains(";EVALUE;6,0;20,0;6,0;"));
        // copie en correction : pas de note ; étudiant sans copie : ABSENT
        assertThat(lignes).anyMatch(l -> l.contains(cas.emails().get(2)) && l.contains(";EN_CORRECTION;;;;"));
        assertThat(lignes).anyMatch(l -> l.contains(cas.emails().get(3)) && l.contains(";ABSENT;;;;"));
        // un nom commençant par « = » ne s'exécute pas dans le tableur
        assertThat(lignes).anyMatch(l -> l.contains("'=cmd|' /C calc'!A0") || l.contains("\"'=cmd|' /C calc'!A0\""));
        assertThat(csv).doesNotContain(";=cmd");
    }

    @Test
    @DisplayName("Export du détail : une ligne par étudiant attendu et par question, note de chaque question seulement si la copie est notée, ni e-mail ni texte")
    void exportDetail() throws Exception {
        Cas cas = evaluationAvecResultats(StatutEvaluation.TERMINEE);
        long attendus = jdbcTemplate.queryForObject(
                "select count(*) from etudiants e join promotions p on p.id = e.promotion_id where p.nom = 'DIC2 Informatique'", Long.class);

        var reponse = mockMvc.perform(get("/enseignant/evaluations/" + cas.evaluationId() + "/export/detail-csv")
                        .header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"detail-" + cas.evaluationId() + ".csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        byte[] octets = reponse.getContentAsByteArray();
        assertThat(octets).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(octets, 3, octets.length - 3, StandardCharsets.UTF_8);
        List<String> lignes = csv.lines().toList();

        assertThat(lignes.get(0)).isEqualTo(ExportResultatsService.ENTETE_DETAIL);
        assertThat(lignes).hasSize((int) attendus * 2 + 1); // deux questions par étudiant
        // Alpha : 8 puis 6 ; Bravo : 4 puis 2 (notes de chaque question, virgule décimale, ordre des questions)
        assertThat(lignes).anyMatch(l -> l.contains(";Alpha;") && l.contains(";EVALUE;1;OUVERTE;10,0;8,0"));
        assertThat(lignes).anyMatch(l -> l.contains(";Alpha;") && l.contains(";EVALUE;2;OUVERTE;10,0;6,0"));
        assertThat(lignes).anyMatch(l -> l.contains(";Bravo;") && l.contains(";EVALUE;1;OUVERTE;10,0;4,0"));
        // copie en correction et étudiant absent : la question est listée, sans note
        assertThat(lignes).anyMatch(l -> l.contains(";Charlie;") && l.endsWith(";EN_CORRECTION;1;OUVERTE;10,0;"));
        assertThat(lignes).anyMatch(l -> l.contains(";ABSENT;1;OUVERTE;10,0;"));
        // aucun e-mail ni texte de réponse
        assertThat(csv).doesNotContain("@ept.sn").doesNotContain("réponse");
    }

    @Test
    @DisplayName("Rapport PDF : en-têtes (application/pdf, attachment, no-store), document PDF valide listant les étudiants, sans e-mail")
    void exportPdf() throws Exception {
        Cas cas = evaluationAvecResultats(StatutEvaluation.TERMINEE);

        var reponse = mockMvc.perform(get("/enseignant/evaluations/" + cas.evaluationId() + "/export/pdf")
                        .header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"resultats-" + cas.evaluationId() + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        assertThat(reponse.getContentType()).startsWith("application/pdf");
        byte[] octets = reponse.getContentAsByteArray();
        assertThat(new String(octets, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(new String(octets, StandardCharsets.ISO_8859_1)).contains("%%EOF");

        // Contenu relu avec la bibliothèque : indicateurs, étudiants et absents présents, jamais d'e-mail
        StringBuilder texte = new StringBuilder();
        com.lowagie.text.pdf.PdfReader lecteur = new com.lowagie.text.pdf.PdfReader(octets);
        com.lowagie.text.pdf.parser.PdfTextExtractor extracteur = new com.lowagie.text.pdf.parser.PdfTextExtractor(lecteur);
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) texte.append(extracteur.getTextFromPage(page)).append('\n');
        lecteur.close();
        assertThat(texte.toString()).contains("Moyenne").contains("Alpha").contains("Bravo").contains("Absent").contains("En attente de note");
        assertThat(texte.toString()).doesNotContain("@ept.sn");
    }

    @Test
    @DisplayName("Notes par étudiant : mêmes lignes que le CSV, sans e-mail, null (jamais 0) sans note, absent signalé")
    void notesParEtudiant() throws Exception {
        Cas cas = evaluationAvecResultats(StatutEvaluation.TERMINEE);
        long attendus = jdbcTemplate.queryForObject(
                "select count(*) from etudiants e join promotions p on p.id = e.promotion_id where p.nom = 'DIC2 Informatique'", Long.class);

        String corps = mockMvc.perform(get("/enseignant/evaluations/" + cas.evaluationId() + "/resultats")
                        .header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.bareme").value(20.0))
                .andExpect(jsonPath("$.lignes.length()").value((int) attendus))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<Double>) JsonPath.read(corps, "$.lignes[?(@.etat=='EVALUE')].note")).containsExactlyInAnyOrder(14.0, 6.0);
        assertThat((List<Object>) JsonPath.read(corps, "$.lignes[?(@.etat=='EN_CORRECTION')].note")).containsExactly((Object) null);
        assertThat((List<Object>) JsonPath.read(corps, "$.lignes[?(@.etat=='EN_CORRECTION')].noteSurVingt")).containsExactly((Object) null);
        assertThat((List<?>) JsonPath.read(corps, "$.lignes[?(@.etat=='ABSENT')].note")).containsOnlyNulls();
        assertThat(corps).doesNotContain("@ept.sn").doesNotContain("email");
    }

    // ---------------------------------------------------------------- outils

    private Long nouvelleEvaluation(StatutEvaluation statut) {
        return transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Stats " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(3));
            e.setDateFermeture(LocalDateTime.now().minusMinutes(30));
            e.setStatut(statut);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            e.addQuestion(new QuestionOuverte("Question un", 1, 10.0));
            e.addQuestion(new QuestionOuverte("Question deux", 2, 10.0));
            return evaluationRepository.save(e).getId();
        });
    }

    private Cas evaluationAvecResultats(StatutEvaluation statut) {
        Long evaluationId = nouvelleEvaluation(statut);
        String a = nouvelEtudiant("Alpha"), b = nouvelEtudiant("Bravo"), c = nouvelEtudiant("Charlie");
        String absent = nouvelEtudiant("=cmd|' /C calc'!A0");
        noterCopie(evaluationId, a, 8.0, 6.0, StatutSoumission.EVALUE);
        noterCopie(evaluationId, b, 4.0, 2.0, StatutSoumission.EVALUE);
        noterCopie(evaluationId, c, 0.0, 0.0, StatutSoumission.EN_CORRECTION);
        return new Cas(evaluationId, List.of(a, b, c, absent));
    }

    /** Copie créée par la vraie passation (évaluation ouverte le temps de la créer), puis réponses et note posées. */
    private void noterCopie(Long evaluationId, String email, double q1, double q2, StatutSoumission statut) {
        Object[] ancien = jdbcTemplate.queryForObject(
                "select statut, date_ouverture, date_fermeture from evaluations where id = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getTimestamp(2), rs.getTimestamp(3)}, evaluationId);
        jdbcTemplate.update("update evaluations set statut = 'EN_COURS', date_ouverture = ?, date_fermeture = ? where id = ?",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), evaluationId);
        Long copieId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        jdbcTemplate.update("update evaluations set statut = ?, date_ouverture = ?, date_fermeture = ? where id = ?",
                ancien[0], ancien[1], ancien[2], evaluationId);

        transactionTemplate.executeWithoutResult(tx -> {
            Soumission copie = soumissionRepository.findById(copieId).orElseThrow();
            List<Question> questions = copie.getEvaluation().getQuestions().stream().sorted(Comparator.comparing(Question::getOrdre)).toList();
            double[] notes = {q1, q2};
            for (int i = 0; i < questions.size(); i++) {
                ReponseOuverte r = new ReponseOuverte();
                r.setQuestion(questions.get(i));
                r.setTexteSoumis("réponse");
                r.setNoteObtenue(notes[i]);
                r.setValideeParProf(statut == StatutSoumission.EVALUE);
                copie.addReponse(r);
            }
            copie.setStatut(statut);
            copie.setDateRemise(LocalDateTime.now().minusMinutes(40));
            if (statut == StatutSoumission.EVALUE) copie.setNoteFinale(q1 + q2);
            soumissionRepository.saveAndFlush(copie);
        });
    }

    private String nouvelEtudiant(String nom) {
        String email = "etu-stat-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant(nom, "Test", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private String nouvelEnseignant() {
        String email = "prof-stat-" + UUID.randomUUID() + "@ept.sn";
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
