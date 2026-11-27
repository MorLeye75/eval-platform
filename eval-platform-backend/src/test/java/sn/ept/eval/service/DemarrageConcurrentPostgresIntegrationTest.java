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
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Démarrage d'une copie pendant une dépublication (point ouvert du DS-04 d'Aminou), sur un vrai PostgreSQL.
 * La dépublication verrouille l'évaluation (FOR UPDATE) puis la repasse en BROUILLON ; un étudiant qui démarre
 * au même instant ne doit pas créer de copie sur ce brouillon. Ignoré si Docker n'est pas disponible.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class DemarrageConcurrentPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SoumissionService soumissionService;

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

    private Long evaluationPublieeOuverte() {
        return transactionTemplate.execute(tx -> {
            Evaluation e = new Evaluation();
            e.setTitre("Dépublication " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(1));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(StatutEvaluation.PUBLIEE);
            e.setResultatsPublies(false);
            e.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            QuestionQCM q = new QuestionQCM("2 + 2 ?", 1, 2.0, false);
            q.addChoix(new ChoixReponse("4", true));
            q.addChoix(new ChoixReponse("5", false));
            e.addQuestion(q);
            return evaluationRepository.save(e).getId();
        });
    }

    private String nouvelEtudiant() {
        String email = "etu-dep-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Depublication", email, "x", "ETU-" + UUID.randomUUID(),
                promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        return email;
    }

    /** Attend qu'une session soit bloquée sur un verrou (le démarrage attend la dépublication). */
    private boolean unVerrouEstAttendu() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Integer enAttente = jdbcTemplate.queryForObject("select count(*) from pg_locks where not granted", Integer.class);
            if (enAttente != null && enAttente > 0) return true;
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }

    @Test
    @DisplayName("Démarrage pendant une dépublication : il attend la dépublication puis refuse, aucune copie sur le brouillon")
    void demarragePendantDepublication_AucuneCopie() throws Exception {
        Long evaluationId = evaluationPublieeOuverte();
        String email = nouvelEtudiant();

        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch valider = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Dépublication (DS-04) : verrou exclusif sur l'évaluation, passage en BROUILLON, validation différée
            Future<?> depublication = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                jdbcTemplate.queryForObject("select statut from evaluations where id = ? for update", String.class, evaluationId);
                jdbcTemplate.update("update evaluations set statut = 'BROUILLON' where id = ?", evaluationId);
                verrouPris.countDown();
                try {
                    valider.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(verrouPris.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> demarrage = pool.submit(() -> {
                try {
                    soumissionService.demarrerOuReprendre(evaluationId, email);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            assertThat(unVerrouEstAttendu()).as("le démarrage attend la fin de la dépublication").isTrue();
            valider.countDown();
            depublication.get(10, TimeUnit.SECONDS);
            Throwable erreur = demarrage.get(10, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from soumissions where evaluation_id = ?", Integer.class, evaluationId))
                    .as("aucune copie créée sur l'évaluation repassée en brouillon").isZero();
            assertThat(erreur).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BROUILLON");
        } finally {
            valider.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Démarrages simultanés de plusieurs étudiants : aucun ne bloque les autres (verrous partagés)")
    void demarragesSimultanes_SansBlocage() throws Exception {
        Long evaluationId = evaluationPublieeOuverte();
        int nb = 8;
        ExecutorService pool = Executors.newFixedThreadPool(nb);
        try {
            CountDownLatch depart = new CountDownLatch(1);
            var resultats = new java.util.ArrayList<Future<Long>>();
            for (int i = 0; i < nb; i++) {
                String email = nouvelEtudiant();
                resultats.add(pool.submit(() -> {
                    depart.await();
                    return soumissionService.demarrerOuReprendre(evaluationId, email).getId();
                }));
            }
            depart.countDown();
            for (Future<Long> r : resultats) {
                assertThat(r.get(20, TimeUnit.SECONDS)).isNotNull();
            }
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from soumissions where evaluation_id = ?", Integer.class, evaluationId)).isEqualTo(nb);
        } finally {
            pool.shutdownNow();
        }
    }
}
