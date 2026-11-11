package sn.ept.eval;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;
import sn.ept.eval.service.AntiTricheService;
import sn.ept.eval.service.ClotureCopiesService;
import sn.ept.eval.service.ResultatsService;
import sn.ept.eval.repository.QuestionRepository;
import sn.ept.eval.service.SoumissionService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie la migration Flyway sur un vrai PostgreSQL : le schéma créé par V1 doit correspondre
 * exactement aux entités (ddl-auto=validate) et la passation doit fonctionner dessus
 * (verrous pessimistes, contraintes). Ignoré si Docker n'est pas disponible (exécuté en CI).
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class MigrationPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private AntiTricheService antiTricheService;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private ClotureCopiesService clotureCopiesService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResultatsService resultatsService;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private sn.ept.eval.repository.PromotionRepository promotionRepository;

    @Test
    @DisplayName("Toutes les migrations sont appliquées et le schéma correspond aux entités")
    void migrationAppliquee() {
        // Le contexte ne démarre que si Hibernate valide le schéma créé par Flyway
        assertThat(flyway.info().pending()).isEmpty();
        // Version courante = la plus haute des migrations du projet, quel que soit son numéro (pas de « 8 » en dur
        // qui casse à chaque nouvelle migration)
        org.flywaydb.core.api.MigrationVersion derniere = java.util.Arrays.stream(flyway.info().all())
                .map(org.flywaydb.core.api.MigrationInfo::getVersion)
                .max(Comparable::compareTo).orElseThrow();
        assertThat(flyway.info().current().getVersion()).isEqualTo(derniere);
    }

    @Test
    @DisplayName("Passation complète sur PostgreSQL (démarrage avec verrou, anti-triche, sauvegarde, soumission)")
    void passationSurPostgres() {
        Long evaluationId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("QCM")).map(Evaluation::getId).findFirst().orElseThrow();

        Long soumissionId = soumissionService.demarrerOuReprendre(evaluationId, "etudiant@ept.sn").getId();
        antiTricheService.enregistrerChangementOnglet(soumissionId, "etudiant@ept.sn");
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(), "etudiant@ept.sn");

        assertThat(soumissionService.soumettreCopie(soumissionId, "etudiant@ept.sn").getStatut())
                .isEqualTo(StatutSoumission.SOUMIS);
    }

    @Test
    @DisplayName("Dernière connexion : mise à jour directe sur la table racine de l'héritage, audit conservé")
    void derniereConnexionSurPostgres() {
        Long id = utilisateurRepository.findByEmail("etudiant@ept.sn").orElseThrow().getId();
        String modifiePar = utilisateurRepository.findById(id).orElseThrow().getModifiePar();
        LocalDateTime connexion = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(utilisateurRepository.enregistrerConnexion(id, connexion)).isEqualTo(1);

        var utilisateur = utilisateurRepository.findById(id).orElseThrow();
        assertThat(utilisateur.getDerniereConnexion()).isEqualTo(connexion);
        assertThat(utilisateur.getModifiePar()).isEqualTo(modifiePar);
    }

    @Test
    @DisplayName("V7 : index partiel de la relance, requêtes de relance et choix de l'exécuteur sur PostgreSQL")
    void suiviCorrectionSurPostgres() {
        assertThat(jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'idx_soumissions_relance'", String.class))
                .contains("WHERE").contains("SOUMIS");

        Long evalCodeId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("code")).map(Evaluation::getId).findFirst().orElseThrow();
        Long soumissionId = soumissionService.demarrerOuReprendre(evalCodeId, "etudiant@ept.sn").getId();
        assertThat(soumissionRepository.evaluationContientCode(soumissionId)).isTrue();

        // Copie restée SOUMIS sans correction : sélectionnée, réservée 5 min, puis plus sélectionnée
        jdbcTemplate.update("update soumissions set statut = 'SOUMIS', prochaine_tentative_le = null where id = ?", soumissionId);
        assertThat(clotureCopiesService.relancerCorrectionsBloquees()).isGreaterThanOrEqualTo(1);
        assertThat(soumissionRepository.findById(soumissionId).orElseThrow().getProchaineTentativeLe()).isNotNull();
    }

    @Test
    @DisplayName("DS-13 : statistiques groupées, barèmes et copies publiées sur PostgreSQL")
    void resultatsSurPostgres() {
        Long evalQcmId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("QCM")).map(Evaluation::getId).findFirst().orElseThrow();
        assertThat(questionRepository.totalPointsParEvaluation(java.util.List.of(evalQcmId)))
                .singleElement().satisfies(t -> assertThat(t.getTotal()).isEqualTo(5.0));

        // Étudiant propre à ce test : la copie de etudiant@ept.sn sert à passationSurPostgres
        String email = "etu-pg-" + java.util.UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new sn.ept.eval.model.Etudiant("Test", "Postgres", email, "x",
                "ETU-" + java.util.UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        Long id = soumissionService.demarrerOuReprendre(evalQcmId, email).getId();
        jdbcTemplate.update("update soumissions set statut = 'EVALUE', note_finale = 4, date_remise = now() where id = ?", id);
        jdbcTemplate.update("update evaluations set resultats_publies = true where id = ?", evalQcmId);

        assertThat(soumissionRepository.statistiquesNotes(java.util.List.of(evalQcmId))).singleElement().satisfies(st -> {
            assertThat(st.getNbCopies()).isEqualTo(1L);
            assertThat(st.getMoyenne()).isEqualTo(4.0);
        });
        var performances = resultatsService.performances(email);
        assertThat(performances.nbEvaluations()).isEqualTo(1);
        assertThat(performances.historique().get(0).noteSur20()).isEqualTo(16.0);
        assertThat(performances.historique().get(0).moyennePromotionSur20()).isNull(); // une seule copie
        assertThat(resultatsService.resultatsCopie(id, email).noteFinale()).isEqualTo(4.0);
    }
}
