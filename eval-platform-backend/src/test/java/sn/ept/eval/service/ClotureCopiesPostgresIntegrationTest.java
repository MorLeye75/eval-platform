package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clôture automatique (DS-11) sur un vrai PostgreSQL : contrairement à H2 (délai de verrou d'1 s par défaut),
 * PostgreSQL attend indéfiniment un verrou détenu ailleurs. La clôture ne doit jamais attendre : une copie
 * verrouillée est passée et reprise au passage suivant, sans retarder les autres ni la relance des corrections.
 * Ignoré si Docker n'est pas disponible.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class ClotureCopiesPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ClotureCopiesService clotureCopiesService;

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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long nouvelleCopieExpiree() {
        Long evalQcmId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("QCM")).map(Evaluation::getId).findFirst().orElseThrow();
        String suffixe = UUID.randomUUID().toString();
        String email = "etu-" + suffixe + "@ept.sn";
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Etudiant", email, "x", "ETU-" + suffixe, dic2));
        Long id = soumissionService.demarrerOuReprendre(evalQcmId, email).getId();
        jdbcTemplate.update("update soumissions set date_debut = ? where id = ?", LocalDateTime.now().minusMinutes(60), id);
        return id;
    }

    private StatutSoumission statut(Long soumissionId) {
        return soumissionRepository.findById(soumissionId).orElseThrow().getStatut();
    }

    @Test
    @DisplayName("Copie verrouillée par une autre transaction : passée sans attendre, les autres sont clôturées")
    void copieVerrouillee_PasseeSansAttendre() throws Exception {
        Long bloquee = nouvelleCopieExpiree();
        Long libre = nouvelleCopieExpiree();

        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Une sauvegarde, une remise ou une transaction bloquée détient le verrou sur « bloquee »
            Future<?> detenteur = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                soumissionRepository.findByIdForUpdate(bloquee).orElseThrow();
                verrouPris.countDown();
                try {
                    liberer.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(verrouPris.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> passage = pool.submit(() -> clotureCopiesService.cloturerCopiesExpirees());

            assertThat(passage.get(5, TimeUnit.SECONDS)).as("la clôture n'attend pas le verrou").isGreaterThanOrEqualTo(1);
            assertThat(statut(libre)).isEqualTo(StatutSoumission.SOUMIS);
            assertThat(statut(bloquee)).isEqualTo(StatutSoumission.EN_COURS);

            // Verrou relâché : la copie est clôturée au passage suivant
            liberer.countDown();
            detenteur.get(10, TimeUnit.SECONDS);
            clotureCopiesService.cloturerCopiesExpirees();
            assertThat(statut(bloquee)).isEqualTo(StatutSoumission.SOUMIS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }
}
