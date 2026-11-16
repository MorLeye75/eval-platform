package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Statistiques;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les lectures de la vue d'ensemble de l'administrateur (DS-18 : sous-requête de barème dans la projection, groupements)
 * s'exécutent sur un vrai PostgreSQL, que H2 ne valide pas fidèlement. Ignoré sans Docker.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class AdminVueEnsemblePostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private AdminVueEnsembleService service;
    @Autowired private SoumissionService soumissionService;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("Tableau de bord, supervision et statistiques s'exécutent sur PostgreSQL ; note ramenée sur 20 par le barème")
    void surPostgres() {
        Promotion promotion = promotionRepository.save(new Promotion("PG-" + UUID.randomUUID(), "2026-2027"));
        String prof = "prof-" + UUID.randomUUID() + "@ept.sn";
        String etudiant = "etu-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Enseignant("Fall", "Awa", prof, passwordEncoder.encode("motDePasse"), "ENS-" + UUID.randomUUID()));
        utilisateurRepository.save(new Etudiant("Test", "Etu", etudiant, passwordEncoder.encode("motDePasse"), "ETU-" + UUID.randomUUID(), promotion));
        Long evaluationId = transactionTemplate.execute(tx -> {
            Evaluation e = new Evaluation();
            e.setTitre("Admin PG " + UUID.randomUUID());
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusMinutes(30));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setResultatsPublies(false);
            e.setEnseignant((Enseignant) utilisateurRepository.findByEmail(prof).orElseThrow());
            e.setPromotions(new HashSet<>(Set.of(promotion)));
            e.addQuestion(new QuestionOuverte("Question", 1, 10.0));
            return evaluationRepository.save(e).getId();
        });
        Long copieId = soumissionService.demarrerOuReprendre(evaluationId, etudiant).getId();
        transactionTemplate.executeWithoutResult(tx -> {
            Soumission copie = soumissionRepository.findById(copieId).orElseThrow();
            copie.setStatut(StatutSoumission.EVALUE);
            copie.setNoteFinale(5.0);                                  // 5 sur 10 → 10 / 20
            soumissionRepository.save(copie);
        });

        assertThat(service.tableauDeBord().nbSoumissions7j()).isGreaterThanOrEqualTo(1);
        assertThat(service.supervision().evaluations()).anyMatch(e -> e.id().equals(evaluationId) && e.nbCommences() == 1 && e.nbSoumis() == 1);
        Statistiques stats = service.statistiques();
        assertThat(stats.promotions()).anyMatch(p -> p.id().equals(promotion.getId())
                && p.nbCopiesNotees() == 1 && Double.valueOf(10.0).equals(p.moyenneSur20()) && Double.valueOf(100.0).equals(p.tauxReussite()));
    }
}
