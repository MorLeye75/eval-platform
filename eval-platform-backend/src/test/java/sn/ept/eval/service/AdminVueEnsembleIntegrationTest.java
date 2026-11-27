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

/** Tableau de bord, supervision globale et statistiques globales de l'administrateur (DS-18) sur H2. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-vue;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class AdminVueEnsembleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SoumissionService soumissionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private AlerteAntiTricheRepository alerteRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("Statistiques : notes sur 20 par promotion ; une promotion sans copie notée a des indicateurs null, jamais 0")
    void statistiques() throws Exception {
        Promotion avecNotes = promotion("Promo-notes-" + UUID.randomUUID());
        Promotion sansNotes = promotion("Promo-vide-" + UUID.randomUUID());
        Long evaluation = evaluation(StatutEvaluation.EN_COURS, avecNotes);
        String etudiant = nouvelEtudiant(avecNotes);
        Long copie = soumissionService.demarrerOuReprendre(evaluation, etudiant).getId();
        noter(copie, StatutSoumission.EVALUE, 8.0);                       // 8 sur un barème de 10 → 16 / 20

        String corps = mockMvc.perform(get("/admin/statistiques").header("Authorization", bearer("admin@ept.sn", "admin123")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<Double>) JsonPath.read(corps, "$.promotions[?(@.nom=='" + avecNotes.getNom() + "')].moyenneSur20")).containsExactly(16.0);
        assertThat((List<Double>) JsonPath.read(corps, "$.promotions[?(@.nom=='" + avecNotes.getNom() + "')].tauxReussite")).containsExactly(100.0);
        assertThat((List<Integer>) JsonPath.read(corps, "$.promotions[?(@.nom=='" + avecNotes.getNom() + "')].nbCopiesNotees")).containsExactly(1);
        assertThat((List<Integer>) JsonPath.read(corps, "$.promotions[?(@.nom=='" + sansNotes.getNom() + "')].nbCopiesNotees")).containsExactly(0);
        assertThat((List<Object>) JsonPath.read(corps, "$.promotions[?(@.nom=='" + sansNotes.getNom() + "')].moyenneSur20")).containsExactly((Object) null);
        assertThat((List<Object>) JsonPath.read(corps, "$.promotions[?(@.nom=='" + sansNotes.getNom() + "')].tauxReussite")).containsExactly((Object) null);
        assertThat(((Number) JsonPath.read(corps, "$.nbCopiesNotees")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Supervision globale : évaluation en cours avec ses effectifs, alertes les plus récentes d'abord")
    void supervision() throws Exception {
        Promotion promotion = promotion("Promo-sup-" + UUID.randomUUID());
        Long evaluation = evaluation(StatutEvaluation.EN_COURS, promotion);
        String a = nouvelEtudiant(promotion);
        nouvelEtudiant(promotion);
        Long copie = soumissionService.demarrerOuReprendre(evaluation, a).getId();
        transactionTemplate.executeWithoutResult(tx -> alerteRepository.save(
                new AlerteAntiTriche("Sorties d'écran répétées (3)", NiveauAlerte.ELEVE, soumissionRepository.findById(copie).orElseThrow())));

        String corps = mockMvc.perform(get("/admin/supervision").header("Authorization", bearer("admin@ept.sn", "admin123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String filtre = "$.evaluations[?(@.id==" + evaluation + ")]";
        assertThat((List<Integer>) JsonPath.read(corps, filtre + ".nbAttendus")).containsExactly(2);
        assertThat((List<Integer>) JsonPath.read(corps, filtre + ".nbCommences")).containsExactly(1);
        assertThat((List<Integer>) JsonPath.read(corps, filtre + ".nbSoumis")).containsExactly(0);
        assertThat((List<String>) JsonPath.read(corps, "$.alertes[?(@.evaluationId==" + evaluation + ")].niveau")).containsExactly("ELEVE");
        assertThat((List<?>) JsonPath.read(corps, "$.alertes")).hasSizeLessThanOrEqualTo(20);
        assertThat(corps).doesNotContain("noteFinale").doesNotContain("texteSoumis");
    }

    @Test
    @DisplayName("Tableau de bord : comptes actifs par rôle, activité sur 7 jours, seulement les services réellement connus")
    void tableauDeBord() throws Exception {
        mockMvc.perform(get("/admin/tableau-de-bord").header("Authorization", bearer("admin@ept.sn", "admin123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.parRole.etudiants").isNumber())
                .andExpect(jsonPath("$.parRole.enseignants").isNumber())
                .andExpect(jsonPath("$.parRole.admins").isNumber())
                .andExpect(jsonPath("$.nbSoumissions7j").isNumber())
                .andExpect(jsonPath("$.services[0].nom").value("Base de données"))
                .andExpect(jsonPath("$.services[1].detail").value("fournisseur : simule"))
                .andExpect(jsonPath("$.services.length()").value(2));
    }

    @Test
    @DisplayName("Un compte désactivé n'est plus compté dans le tableau de bord")
    void desactivesNonComptes() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        Promotion promotion = promotion("Promo-actifs-" + UUID.randomUUID());
        String email = nouvelEtudiant(promotion);
        int avant = ((Number) JsonPath.read(mockMvc.perform(get("/admin/tableau-de-bord").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(), "$.parRole.etudiants")).intValue();
        transactionTemplate.executeWithoutResult(tx -> {
            Utilisateur u = utilisateurRepository.findByEmail(email).orElseThrow();
            u.setActif(false);
            utilisateurRepository.save(u);
        });
        mockMvc.perform(get("/admin/tableau-de-bord").header("Authorization", admin))
                .andExpect(jsonPath("$.parRole.etudiants").value(avant - 1));
    }

    @Test
    @DisplayName("Accès : enseignant 403, étudiant 403, sans jeton 401")
    void acces() throws Exception {
        for (String route : new String[]{"/admin/tableau-de-bord", "/admin/supervision", "/admin/statistiques"}) {
            mockMvc.perform(get(route).header("Authorization", bearer("prof@ept.sn", "prof123"))).andExpect(status().isForbidden());
            mockMvc.perform(get(route).header("Authorization", bearer("etudiant@ept.sn", "etudiant123"))).andExpect(status().isForbidden());
            mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------- outils

    private Promotion promotion(String nom) {
        return promotionRepository.save(new Promotion(nom, "2026-2027"));
    }

    private Long evaluation(StatutEvaluation statut, Promotion promotion) {
        return transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Vue admin " + UUID.randomUUID());
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusMinutes(30));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(statut);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotion)));
            e.addQuestion(new QuestionOuverte("Question un", 1, 5.0));
            e.addQuestion(new QuestionOuverte("Question deux", 2, 5.0));
            return evaluationRepository.save(e).getId();
        });
    }

    private void noter(Long copieId, StatutSoumission statut, double note) {
        transactionTemplate.executeWithoutResult(tx -> {
            Soumission copie = soumissionRepository.findById(copieId).orElseThrow();
            copie.setStatut(statut);
            copie.setNoteFinale(note);
            soumissionRepository.save(copie);
        });
    }

    private String nouvelEtudiant(Promotion promotion) {
        String email = "etu-adm-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Etu", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotion));
        return email;
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }
}
