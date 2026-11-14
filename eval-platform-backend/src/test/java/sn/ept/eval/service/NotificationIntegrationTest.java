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
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications internes (DS-15) : production dans la transaction de l'événement, consultation par le seul
 * destinataire, marquage comme lue, purge. Base H2 propre à cette classe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notifications;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationService notificationService;
    @Autowired private MesNotificationsService mesNotificationsService;
    @Autowired private SoumissionService soumissionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtudiantRepository etudiantRepository;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Promotion promo;

    /** Promotion à part : ses seuls étudiants sont ceux de chaque test. */
    @BeforeEach
    void setUp() {
        promo = promotionRepository.save(new Promotion("Promo " + UUID.randomUUID(), "2026"));
    }

    private Long evaluation(boolean resultatsPublies) {
        return transactionTemplate.execute(tx -> {
            Evaluation e = new Evaluation();
            e.setTitre("Réseaux " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(1));
            e.setDateFermeture(LocalDateTime.of(2026, 12, 3, 18, 0));
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setResultatsPublies(resultatsPublies);
            e.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            e.setPromotions(new HashSet<>(Set.of(promo)));
            QuestionQCM q = new QuestionQCM("2 + 2 ?", 1, 2.0, false);
            q.addChoix(new ChoixReponse("4", true));
            q.addChoix(new ChoixReponse("5", false));
            e.addQuestion(q);
            return evaluationRepository.save(e).getId();
        });
    }

    private String etudiant() {
        String email = "etu-notif-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Sarr", "Fatou", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promo));
        return email;
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String r = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(r, "$.token");
    }

    private Long id(String email) {
        return utilisateurRepository.findByEmail(email).orElseThrow().getId();
    }

    private int nombre(String email) {
        return jdbcTemplate.queryForObject("select count(*) from notifications where utilisateur_id = ?", Integer.class, id(email));
    }

    private Long copie(Long evaluationId, String email, StatutSoumission statut) {
        Long sid = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        jdbcTemplate.update("update soumissions set statut = ?, note_finale = 2 where id = ?", statut.name(), sid);
        return sid;
    }

    @Test
    @DisplayName("L'implémentation enregistrée en base est celle injectée, pas le service par défaut d'Aminou")
    void implementationInjectee() {
        assertThat(notificationService).isInstanceOf(NotificationServiceEnBase.class);
    }

    @Test
    @DisplayName("Évaluation ouverte : une notification par étudiant de ses promotions, plus une pour l'enseignant")
    void evaluationOuverte() {
        String a = etudiant();
        String b = etudiant();
        Long evalId = evaluation(false);
        int avantProf = nombre("prof@ept.sn");

        transactionTemplate.executeWithoutResult(tx ->
                notificationService.evaluationOuverte(evaluationRepository.findById(evalId).orElseThrow()));

        assertThat(nombre(a)).isEqualTo(1);
        assertThat(nombre(b)).isEqualTo(1);
        assertThat(nombre("prof@ept.sn")).isEqualTo(avantProf + 1);
        String message = jdbcTemplate.queryForObject(
                "select message from notifications where utilisateur_id = ?", String.class, id(a));
        assertThat(message).contains("est ouverte jusqu'au 3 déc. 2026 à 18h00");
        assertThat(jdbcTemplate.queryForObject("select evaluation_id from notifications where utilisateur_id = ?",
                Long.class, id(a))).isEqualTo(evalId);
    }

    @Test
    @DisplayName("Résultats publiés : une notification par copie notée, avec la copie ; rien pour une copie encore en correction")
    void resultatsPublies() {
        String notee = etudiant();
        String enCorrection = etudiant();
        Long evalId = evaluation(true);
        Long copieNotee = copie(evalId, notee, StatutSoumission.EVALUE);
        copie(evalId, enCorrection, StatutSoumission.EN_CORRECTION);

        transactionTemplate.executeWithoutResult(tx ->
                notificationService.resultatsPublies(evaluationRepository.findById(evalId).orElseThrow()));

        assertThat(nombre(notee)).isEqualTo(1);
        assertThat(nombre(enCorrection)).isZero();
        assertThat(jdbcTemplate.queryForObject("select soumission_id from notifications where utilisateur_id = ?",
                Long.class, id(notee))).isEqualTo(copieNotee);
    }

    @Test
    @DisplayName("Résultat d'une copie notée après la publication : une seule notification, même si l'appel est répété")
    void resultatPublie_SansDoublon() {
        String email = etudiant();
        Long sid = copie(evaluation(true), email, StatutSoumission.EVALUE);

        for (int i = 0; i < 2; i++) {
            transactionTemplate.executeWithoutResult(tx ->
                    notificationService.resultatPublie(soumissionRepository.findById(sid).orElseThrow()));
        }
        transactionTemplate.executeWithoutResult(tx ->
                notificationService.resultatsPublies(soumissionRepository.findById(sid).orElseThrow().getEvaluation()));

        assertThat(nombre(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("Correction automatique terminée après la publication des résultats : l'étudiant est prévenu")
    void correctionApresPublication_Notifiee() throws Exception {
        String publie = etudiant();
        String nonPublie = etudiant();
        Long avecPublication = evaluation(true);
        Long sansPublication = evaluation(false);

        Long a = remettre(avecPublication, publie);
        Long b = remettre(sansPublication, nonPublie);

        assertThat(statut(a)).isEqualTo(StatutSoumission.EVALUE);
        assertThat(statut(b)).isEqualTo(StatutSoumission.EVALUE);
        assertThat(nombre(publie)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select soumission_id from notifications where utilisateur_id = ?",
                Long.class, id(publie))).isEqualTo(a);
        assertThat(nombre(nonPublie)).isZero(); // résultats pas publiés : aucune note annoncée (DS-13)
    }

    private Long remettre(Long evaluationId, String email) throws InterruptedException {
        Long sid = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        Long question = questionRepository.findByEvaluationIdOrderByOrdreAsc(evaluationId).get(0).getId();
        Long choix = transactionTemplate.execute(tx -> ((QuestionQCM) org.hibernate.Hibernate.unproxy(
                questionRepository.findById(question).orElseThrow())).getChoix().get(0).getId());
        soumissionService.sauvegarderReponses(sid, new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(question, null, null, List.of(choix)))), email);
        soumissionService.soumettreCopie(sid, email);
        for (int i = 0; i < 100 && statut(sid) != StatutSoumission.EVALUE; i++) Thread.sleep(50);
        return sid;
    }

    private StatutSoumission statut(Long id) {
        return soumissionRepository.findById(id).orElseThrow().getStatut();
    }

    @Test
    @DisplayName("Consultation : seulement mes notifications, les plus récentes d'abord, compteur des non lues")
    void consultation() throws Exception {
        String moi = etudiant();
        String autre = etudiant();
        Long evalId = evaluation(true);
        Long sid = copie(evalId, moi, StatutSoumission.EVALUE);
        transactionTemplate.executeWithoutResult(tx -> {
            Evaluation e = evaluationRepository.findById(evalId).orElseThrow();
            notificationService.evaluationOuverte(e);
            notificationService.resultatsPublies(e);
        });
        String auth = bearer(moi, "motDePasse");

        mockMvc.perform(get("/notifications").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.notifications", hasSize(2)))
                .andExpect(jsonPath("$.notifications[0].type").value("RESULTAT_PUBLIE"))
                .andExpect(jsonPath("$.notifications[0].soumissionId").value(sid.intValue()))
                .andExpect(jsonPath("$.notifications[0].evaluationId").value(evalId.intValue()))
                .andExpect(jsonPath("$.notifications[0].lue").value(false))
                .andExpect(jsonPath("$.notifications[1].type").value("EVALUATION_OUVERTE"));
        mockMvc.perform(get("/notifications").param("page", "0").param("taille", "1").header("Authorization", auth))
                .andExpect(jsonPath("$.notifications", hasSize(1)))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/notifications/non-lues/nombre").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(2));
        // L'autre étudiant a sa propre notification « ouverte », pas la mienne
        mockMvc.perform(get("/notifications").header("Authorization", bearer(autre, "motDePasse")))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("Marquer comme lue : 204 pour la mienne, 404 pour celle d'un autre (son existence n'est pas révélée)")
    void marquerLue() throws Exception {
        String moi = etudiant();
        String autre = etudiant();
        Long evalId = evaluation(false);
        transactionTemplate.executeWithoutResult(tx ->
                notificationService.evaluationOuverte(evaluationRepository.findById(evalId).orElseThrow()));
        Long mienne = jdbcTemplate.queryForObject("select id from notifications where utilisateur_id = ?", Long.class, id(moi));
        Long sienne = jdbcTemplate.queryForObject("select id from notifications where utilisateur_id = ?", Long.class, id(autre));
        String auth = bearer(moi, "motDePasse");

        mockMvc.perform(put("/notifications/{id}/lire", mienne).header("Authorization", auth)).andExpect(status().isNoContent());
        mockMvc.perform(put("/notifications/{id}/lire", sienne).header("Authorization", auth)).andExpect(status().isNotFound());
        mockMvc.perform(put("/notifications/{id}/lire", 999_999L).header("Authorization", auth)).andExpect(status().isNotFound());

        mockMvc.perform(get("/notifications/non-lues/nombre").header("Authorization", auth)).andExpect(jsonPath("$.nombre").value(0));
        assertThat(jdbcTemplate.queryForObject("select lue from notifications where id = ?", Boolean.class, sienne)).isFalse();
    }

    @Test
    @DisplayName("Tout marquer comme lu : mes notifications seulement")
    void toutMarquerLu() throws Exception {
        String moi = etudiant();
        String autre = etudiant();
        Long evalId = evaluation(true);
        copie(evalId, moi, StatutSoumission.EVALUE);
        transactionTemplate.executeWithoutResult(tx -> {
            Evaluation e = evaluationRepository.findById(evalId).orElseThrow();
            notificationService.evaluationOuverte(e);
            notificationService.resultatsPublies(e);
        });
        String auth = bearer(moi, "motDePasse");

        mockMvc.perform(put("/notifications/lire-tout").header("Authorization", auth)).andExpect(status().isNoContent());

        mockMvc.perform(get("/notifications/non-lues/nombre").header("Authorization", auth)).andExpect(jsonPath("$.nombre").value(0));
        mockMvc.perform(get("/notifications/non-lues/nombre").header("Authorization", bearer(autre, "motDePasse")))
                .andExpect(jsonPath("$.nombre").value(1));
    }

    @Test
    @DisplayName("Sans jeton : 401 sur toutes les routes")
    void sansJeton_401() throws Exception {
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications/non-lues/nombre")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/notifications/lire-tout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Purge : les notifications lues de plus de 6 mois sont supprimées ; les non lues et les récentes restent")
    void purge() {
        String email = etudiant();
        Long evalId = evaluation(false);
        transactionTemplate.executeWithoutResult(tx ->
                notificationService.evaluationOuverte(evaluationRepository.findById(evalId).orElseThrow()));
        Long u = id(email);
        jdbcTemplate.update("insert into notifications (utilisateur_id, message, type, lue, date_envoi) values (?, 'vieille lue', 'EVALUATION_OUVERTE', true, ?)",
                u, LocalDateTime.now().minusMonths(7));
        jdbcTemplate.update("insert into notifications (utilisateur_id, message, type, lue, date_envoi) values (?, 'vieille non lue', 'EVALUATION_OUVERTE', false, ?)",
                u, LocalDateTime.now().minusMonths(7));

        mesNotificationsService.purger();

        assertThat(jdbcTemplate.queryForList("select message from notifications where utilisateur_id = ?", String.class, u))
                .hasSize(2).doesNotContain("vieille lue").contains("vieille non lue");
    }
}
