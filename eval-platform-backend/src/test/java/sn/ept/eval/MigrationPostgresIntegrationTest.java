package sn.ept.eval;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.service.AntiTricheService;
import sn.ept.eval.service.SoumissionService;

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

    @Test
    @DisplayName("Toutes les migrations sont appliquées et le schéma correspond aux entités")
    void migrationAppliquee() {
        // Le contexte ne démarre que si Hibernate valide le schéma créé par Flyway
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
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
}
