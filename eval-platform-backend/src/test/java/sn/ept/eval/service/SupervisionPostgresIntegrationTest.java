package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.dto.evaluation.SupervisionResponse;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
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

/**
 * Les requêtes de comptage de la supervision (DS-17 : trim, size sur une collection) s'exécutent sur un vrai
 * PostgreSQL, que H2 ne valide pas fidèlement. Ignoré sans Docker.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class SupervisionPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private SupervisionService supervisionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("La supervision s'exécute sur PostgreSQL : étudiants attendus listés, aucun commencé, aucune alerte")
    void supervisionSurPostgres() {
        String email = "prof-" + UUID.randomUUID() + "@ept.sn";
        Long evaluationId = transactionTemplate.execute(tx -> {
            Promotion promotion = promotionRepository.save(new Promotion("DIC-" + UUID.randomUUID(), "2026"));
            Enseignant prof = utilisateurRepository.save(new Enseignant("Sow", "Mamadou", email, "x", "ENS-" + UUID.randomUUID()));
            utilisateurRepository.save(new Etudiant("Diop", "Moussa", "e-" + UUID.randomUUID() + "@ept.sn", "x", "N-" + UUID.randomUUID(), promotion));
            Evaluation e = new Evaluation();
            e.setTitre("Supervision PG");
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusMinutes(30));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotion)));
            e.addQuestion(new QuestionOuverte("Question", 1, 5.0));
            return evaluationRepository.save(e).getId();
        });

        SupervisionResponse reponse = supervisionService.supervision(evaluationId, email);

        assertThat(reponse.nbAttendus()).isEqualTo(1);
        assertThat(reponse.nbCommences()).isZero();
        assertThat(reponse.nbAlertes()).isZero();
        assertThat(reponse.etudiants().get(0).etat()).isEqualTo(SupervisionResponse.EtatCopie.NON_COMMENCE);
    }
}
