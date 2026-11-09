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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultation des résultats (DS-13) par l'API, sur H2 : états de la copie, accès, corrigé après la fermeture,
 * cas masqués, seuil d'anonymat de la moyenne et tableau de bord. Une évaluation neuve par test : les effectifs
 * ne se mélangent pas.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:resultats;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class ResultatsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long evalId;
    private Long q1;      // 2 pts, bonne réponse « 4 »
    private Long q2;      // 3 pts, bonnes réponses « C » et « C++ »
    private Long choix4;
    private Long choix5;
    private Long choixPython;

    @BeforeEach
    void setUp() {
        evalId = nouvelleEvaluation(LocalDateTime.now().plusDays(1));
        List<Question> questions = transactionTemplate.execute(tx -> {
            List<Question> qs = questionRepository.findByEvaluationIdOrderByOrdreAsc(evalId);
            qs.forEach(q -> ((QuestionQCM) q).getChoix().size());
            return qs;
        });
        q1 = questions.get(0).getId();
        q2 = questions.get(1).getId();
        choix4 = idChoix(questions.get(0), "4");
        choix5 = idChoix(questions.get(0), "5");
        choixPython = idChoix(questions.get(1), "Python");
    }

    private static Long idChoix(Question q, String texte) {
        return ((QuestionQCM) q).getChoix().stream().filter(c -> c.getTexte().equals(texte)).findFirst().orElseThrow().getId();
    }

    /** Évaluation QCM sur 5 points, ouverte, pour la promotion DIC2. */
    private Long nouvelleEvaluation(LocalDateTime fermeture) {
        return transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Résultats " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(1));
            e.setDateFermeture(fermeture);
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            QuestionQCM simple = new QuestionQCM("2 + 2 ?", 1, 2.0, false);
            simple.addChoix(new ChoixReponse("4", true));
            simple.addChoix(new ChoixReponse("5", false));
            QuestionQCM multiple = new QuestionQCM("Langages compilés ?", 2, 3.0, true);
            multiple.addChoix(new ChoixReponse("C", true));
            multiple.addChoix(new ChoixReponse("Python", false));
            multiple.addChoix(new ChoixReponse("C++", true));
            e.addQuestion(simple);
            e.addQuestion(multiple);
            return evaluationRepository.save(e).getId();
        });
    }

    private String nouvelEtudiant() {
        String email = "etu-res-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Resultats", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }

    /** Copie remise par la vraie passation : q1 juste (2 pts), q2 fausse (Python coché) → 2/5 après correction. */
    private Long copieRemise(String email) throws InterruptedException {
        Long id = soumissionService.demarrerOuReprendre(evalId, email).getId();
        soumissionService.sauvegarderReponses(id, new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(q1, null, null, List.of(choix4)),
                new ReponseItemDto(q2, null, null, List.of(choixPython)))), email);
        soumissionService.soumettreCopie(id, email);
        for (int i = 0; i < 100 && statut(id) != StatutSoumission.EVALUE; i++) Thread.sleep(50);
        assertThat(statut(id)).isEqualTo(StatutSoumission.EVALUE);
        return id;
    }

    /** Copie d'un autre étudiant, notée directement (effectif de la promotion). */
    private void copieNotee(Long evaluationId, double note, LocalDateTime remise) {
        Long id = soumissionService.demarrerOuReprendre(evaluationId, nouvelEtudiant()).getId();
        jdbcTemplate.update("update soumissions set statut = 'EVALUE', note_finale = ?, date_remise = ? where id = ?",
                note, remise, id);
    }

    private StatutSoumission statut(Long id) {
        return soumissionRepository.findById(id).orElseThrow().getStatut();
    }

    private void publier(Long evaluationId) {
        jdbcTemplate.update("update evaluations set resultats_publies = true where id = ?", evaluationId);
    }

    @Test
    @DisplayName("Copie en cours, puis remise non publiée (aucune note, même automatique), puis publiée")
    void etats_NonRemise_EnAttente_Publies() throws Exception {
        String email = nouvelEtudiant();
        String auth = bearer(email, "motDePasse");
        Long enCours = soumissionService.demarrerOuReprendre(evalId, email).getId();

        mockMvc.perform(get("/soumissions/{id}/resultats", enCours).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("NON_REMISE"))
                .andExpect(jsonPath("$.evaluationId").value(evalId.intValue()));

        jdbcTemplate.update("delete from reponses_etudiant where soumission_id = ?", enCours);
        jdbcTemplate.update("delete from soumissions where id = ?", enCours);
        Long id = copieRemise(email); // corrigée automatiquement (EVALUE), pas encore publiée

        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.dateRemise").isNotEmpty())
                .andExpect(jsonPath("$.noteFinale").doesNotExist())
                .andExpect(jsonPath("$.questions").isEmpty());

        publier(evalId);

        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("PUBLIES"))
                .andExpect(jsonPath("$.noteFinale").value(2.0))
                .andExpect(jsonPath("$.totalPoints").value(5.0))
                .andExpect(jsonPath("$.questions", hasSize(2)))
                // Avant la fermeture : note totale seulement, les points par question QCM révéleraient le corrigé
                .andExpect(jsonPath("$.questions[0].noteObtenue").doesNotExist())
                .andExpect(jsonPath("$.questions[1].noteObtenue").doesNotExist())
                .andExpect(jsonPath("$.questions[1].choix[?(@.texte == 'Python')].coche").value(true));
    }

    @Test
    @DisplayName("Accès : copie d'un autre étudiant 403, copie inexistante 404, enseignant 403, sans jeton 401")
    void acces() throws Exception {
        Long id = copieRemise(nouvelEtudiant());
        String autre = bearer(nouvelEtudiant(), "motDePasse");

        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", autre))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/soumissions/{id}/resultats", 999_999L).header("Authorization", autre))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/soumissions/{id}/resultats", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/etudiant/performances")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Corrigé : bonnes réponses cachées avant la fermeture + 2 min, montrées après")
    void corrige_ApresLaFermetureSeulement() throws Exception {
        String email = nouvelEtudiant();
        Long id = copieRemise(email);
        publier(evalId);
        String auth = bearer(email, "motDePasse");

        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", auth))
                .andExpect(jsonPath("$.corrigeVisible").value(false))
                .andExpect(jsonPath("$.questions[*].choix[*].estCorrect", everyItem(nullValue())))
                .andExpect(jsonPath("$.noteFinale").value(2.0)) // la note totale, elle, est visible
                // « 2/2 points » à côté des choix cochés dirait lesquels sont justes : points masqués aussi
                .andExpect(jsonPath("$.questions[0].noteObtenue").doesNotExist())
                .andExpect(jsonPath("$.questions[1].noteObtenue").doesNotExist());

        // Fermée depuis 1 min : encore dans la marge, quelqu'un peut être en train de remettre
        jdbcTemplate.update("update evaluations set date_fermeture = ? where id = ?", LocalDateTime.now().minusMinutes(1), evalId);
        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", auth))
                .andExpect(jsonPath("$.corrigeVisible").value(false));

        jdbcTemplate.update("update evaluations set date_fermeture = ? where id = ?", LocalDateTime.now().minusMinutes(3), evalId);
        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", auth))
                .andExpect(jsonPath("$.corrigeVisible").value(true))
                .andExpect(jsonPath("$.questions[0].noteObtenue").value(2.0))
                .andExpect(jsonPath("$.questions[1].noteObtenue").value(0.0))
                .andExpect(jsonPath("$.questions[0].choix[?(@.id == " + choix4 + ")].estCorrect").value(true))
                .andExpect(jsonPath("$.questions[0].choix[?(@.id == " + choix5 + ")].estCorrect").value(false));
    }

    @Test
    @DisplayName("Moyenne de la promotion : null avec 4 copies notées, affichée avec l'écart à partir de 5")
    void moyenne_APartirDe5Copies() throws Exception {
        String email = nouvelEtudiant();
        Long id = copieRemise(email); // 2/5
        publier(evalId);
        String auth = bearer(email, "motDePasse");
        LocalDateTime remise = LocalDateTime.now();
        copieNotee(evalId, 4.0, remise);
        copieNotee(evalId, 5.0, remise);
        copieNotee(evalId, 1.0, remise);
        // Une copie encore en correction ne compte pas
        Long enCorrection = soumissionService.demarrerOuReprendre(evalId, nouvelEtudiant()).getId();
        jdbcTemplate.update("update soumissions set statut = 'EN_CORRECTION', note_finale = 0 where id = ?", enCorrection);

        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", auth))
                .andExpect(jsonPath("$.moyennePromotion").doesNotExist())
                .andExpect(jsonPath("$.ecartMoyenne").doesNotExist());

        copieNotee(evalId, 3.0, remise); // 5e copie notée : (2 + 4 + 5 + 1 + 3) / 5 = 3

        mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", auth))
                .andExpect(jsonPath("$.moyennePromotion").value(3.0))
                .andExpect(jsonPath("$.ecartMoyenne").value(-1.0));
    }

    @Test
    @DisplayName("Cas de test masqué : verdict et temps seulement ; cas visible : entrée, sorties et message")
    void casMasques_VerdictSeulement() throws Exception {
        Long evalCodeId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("code")).map(Evaluation::getId).findFirst().orElseThrow();
        String email = nouvelEtudiant();
        Long questionId = questionRepository.findByEvaluationIdOrderByOrdreAsc(evalCodeId).get(0).getId();
        Long id = soumissionService.demarrerOuReprendre(evalCodeId, email).getId();
        ReponseItemDto reponse = new ReponseItemDto(questionId, null, "print('true')", List.of());
        reponse.setLangage("python");
        soumissionService.sauvegarderReponses(id, new SauvegardeReponsesRequest(List.of(reponse)), email);
        // Correction simulée : le cas masqué (kayak) échoue, sa sortie d'erreur recopie l'entrée
        transactionTemplate.executeWithoutResult(tx -> {
            Soumission s = soumissionRepository.findById(id).orElseThrow();
            ReponseCode rc = (ReponseCode) s.getReponses().get(0);
            QuestionCode q = (QuestionCode) org.hibernate.Hibernate.unproxy(rc.getQuestion());
            for (CasDeTest cas : q.getCasTests()) {
                ResultatCasTest r = new ResultatCasTest();
                r.setCasDeTest(cas);
                boolean masque = cas.getEstMasque();
                r.setEstValide(!masque);
                r.setVerdict(masque ? VerdictCas.ERREUR_EXECUTION : VerdictCas.REUSSI);
                r.setSortieObtenue(masque ? "secret" : cas.getSortieAttendue());
                r.setMessageErreur(masque ? "entrée lue : " + cas.getEntree() : null);
                r.setTempsExecution(0.02);
                rc.addResultat(r);
            }
            rc.setNoteObtenue(3.33);
            s.setStatut(StatutSoumission.EVALUE);
            s.setNoteFinale(3.33);
        });
        publier(evalCodeId);

        String json = mockMvc.perform(get("/soumissions/{id}/resultats", id).header("Authorization", bearer(email, "motDePasse")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].codeSoumis").value("print('true')"))
                .andExpect(jsonPath("$.questions[0].langage").value("python"))
                .andExpect(jsonPath("$.questions[0].casTests", hasSize(3)))
                .andExpect(jsonPath("$.questions[0].casTests[?(@.masque == true)].verdict").value("ERREUR_EXECUTION"))
                .andExpect(jsonPath("$.questions[0].casTests[?(@.masque == true)].tempsExecution").value(0.02))
                .andExpect(jsonPath("$.questions[0].casTests[?(@.masque == false)].entree", containsInAnyOrder("radar", "bonjour")))
                .andExpect(jsonPath("$.questions[0].casTests[?(@.masque == false)].sortieObtenue", containsInAnyOrder("true", "false")))
                .andReturn().getResponse().getContentAsString();
        // Rien du cas masqué ne fuit, sous aucun champ
        assertThat(json).doesNotContain("kayak").doesNotContain("secret");
    }

    @Test
    @DisplayName("Tableau de bord : notes sur 20, évaluations publiées seulement, écart calculé à périmètre égal")
    void performances() throws Exception {
        String email = nouvelEtudiant();
        String auth = bearer(email, "motDePasse");

        mockMvc.perform(get("/etudiant/performances").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historique").isEmpty())
                .andExpect(jsonPath("$.nbEvaluations").value(0))
                .andExpect(jsonPath("$.moyenneGenerale").doesNotExist());

        // A : 2/5 (8/20), promotion de 5 copies, moyenne 3/5 (12/20)
        Long idA = copieRemise(email);
        jdbcTemplate.update("update soumissions set date_remise = ? where id = ?", LocalDateTime.now().minusDays(2), idA);
        LocalDateTime remise = LocalDateTime.now().minusDays(2);
        for (double note : new double[]{4.0, 5.0, 1.0, 3.0}) copieNotee(evalId, note, remise);
        publier(evalId);
        // B : 5/5 (20/20), seule copie notée : pas de moyenne
        Long evalB = nouvelleEvaluation(LocalDateTime.now().plusDays(1));
        Long idB = soumissionService.demarrerOuReprendre(evalB, email).getId();
        jdbcTemplate.update("update soumissions set statut = 'EVALUE', note_finale = 5, date_remise = ? where id = ?",
                LocalDateTime.now().minusDays(1), idB);
        publier(evalB);
        // C : notée mais non publiée, ignorée
        Long evalC = nouvelleEvaluation(LocalDateTime.now().plusDays(1));
        Long idC = soumissionService.demarrerOuReprendre(evalC, email).getId();
        jdbcTemplate.update("update soumissions set statut = 'EVALUE', note_finale = 1, date_remise = ? where id = ?",
                LocalDateTime.now(), idC);

        mockMvc.perform(get("/etudiant/performances").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbEvaluations").value(2))
                .andExpect(jsonPath("$.historique[0].soumissionId").value(idA.intValue()))
                .andExpect(jsonPath("$.historique[0].noteSur20").value(8.0))
                .andExpect(jsonPath("$.historique[0].moyennePromotionSur20").value(12.0))
                .andExpect(jsonPath("$.historique[1].soumissionId").value(idB.intValue()))
                .andExpect(jsonPath("$.historique[1].noteSur20").value(20.0))
                .andExpect(jsonPath("$.historique[1].moyennePromotionSur20").doesNotExist())
                .andExpect(jsonPath("$.moyenneGenerale").value(14.0))
                .andExpect(jsonPath("$.moyennePromotionGenerale").value(12.0))
                // Comparé sur A seulement (B n'a pas de moyenne) : 8 - 12
                .andExpect(jsonPath("$.ecartMoyenne").value(-4.0));
    }
}
