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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Requêtes et verrous du module enseignant sur un vrai PostgreSQL (DS-05, DS-06, DS-07) : mises à jour
 * conditionnelles atomiques, version incrémentée, et verrou de l'évaluation à l'enregistrement d'un tiers-temps.
 * H2 ne reproduit pas fidèlement les verrous : ces comportements ne sont donc validés qu'ici. Ignoré sans Docker.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class EnseignantPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private EvaluationStatutService statutService;
    @Autowired private AmenagementTempsService amenagementTempsService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("PUBLIEE ouverte → EN_COURS : la version avance et la notification part une seule fois, même si le passage est répété")
    void ouverture_VersionEtUneSeuleNotification() {
        Long id = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(2));
        Long versionAvant = evaluationRepository.findById(id).orElseThrow().getVersion();

        statutService.mettreAJourStatuts();
        statutService.mettreAJourStatuts();

        Evaluation apres = evaluationRepository.findById(id).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutEvaluation.EN_COURS);
        assertThat(apres.getVersion()).isGreaterThan(versionAvant);
        verify(notificationService, times(1)).evaluationOuverte(argThat(e -> e.getId().equals(id)));
    }

    @Test
    @DisplayName("Deux passages simultanés de la tâche sur PostgreSQL : une seule transition, une seule notification")
    void deuxPassagesSimultanes() throws Exception {
        Long id = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(2));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            Runnable passage = () -> {
                try {
                    depart.await();
                    statutService.mettreAJourStatuts();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            CompletableFuture<Void> p1 = CompletableFuture.runAsync(passage, pool);
            CompletableFuture<Void> p2 = CompletableFuture.runAsync(passage, pool);
            depart.countDown();
            p1.get(30, TimeUnit.SECONDS);
            p2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(evaluationRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.EN_COURS);
        verify(notificationService, times(1)).evaluationOuverte(argThat(e -> e.getId().equals(id)));
    }

    @Test
    @DisplayName("Publication des résultats : de plusieurs appels simultanés, un seul passe resultatsPublies à vrai")
    void publicationDesResultats_Atomique() throws Exception {
        Long id = evaluation(StatutEvaluation.TERMINEE, LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));
        int appels = 6;
        ExecutorService pool = Executors.newFixedThreadPool(appels);
        CountDownLatch depart = new CountDownLatch(1);
        AtomicInteger gagnants = new AtomicInteger();
        try {
            CompletableFuture<?>[] taches = new CompletableFuture<?>[appels];
            for (int i = 0; i < appels; i++) {
                taches[i] = CompletableFuture.runAsync(() -> {
                    try {
                        depart.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Integer lignes = transactionTemplate.execute(tx -> evaluationRepository.publierResultatsSiNonPublies(id));
                    if (lignes != null && lignes == 1) gagnants.incrementAndGet();
                }, pool);
            }
            depart.countDown();
            CompletableFuture.allOf(taches).get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(gagnants.get()).isEqualTo(1);
        assertThat(evaluationRepository.findById(id).orElseThrow().getResultatsPublies()).isTrue();
    }

    @Test
    @DisplayName("L'enregistrement d'un tiers-temps attend le verrou partagé pris par un démarrage de copie (DS-06 / DS-10)")
    void amenagementAttendLeVerrouPartage() throws Exception {
        Long id = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(4));
        Long etudiantId = nouvelEtudiant();
        CountDownLatch verrouPris = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Un démarrage de copie : verrou partagé sur l'évaluation, gardé 2 secondes
            CompletableFuture<Void> demarrage = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(tx -> {
                evaluationRepository.findByIdForShare(id).orElseThrow();
                verrouPris.countDown();
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }), pool);
            assertThat(verrouPris.await(10, TimeUnit.SECONDS)).isTrue();

            long debut = System.nanoTime();
            CompletableFuture<Void> amenagement = CompletableFuture.runAsync(
                    () -> amenagementTempsService.definir(id, etudiantId, 90, "prof@ept.sn"), pool);

            // Tant que le démarrage tient son verrou, l'aménagement n'aboutit pas
            Thread.sleep(700);
            assertThat(amenagement.isDone()).as("l'aménagement doit attendre le verrou partagé").isFalse();

            amenagement.get(30, TimeUnit.SECONDS);
            demarrage.get(30, TimeUnit.SECONDS);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - debut)).isGreaterThanOrEqualTo(1_000);
        } finally {
            pool.shutdownNow();
        }
    }

    private Long evaluation(StatutEvaluation statut, LocalDateTime ouverture, LocalDateTime fermeture) {
        return transactionTemplate.execute(tx -> {
            Evaluation e = new Evaluation();
            e.setTitre("Enseignant PG " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(ouverture);
            e.setDateFermeture(fermeture);
            e.setStatut(statut);
            e.setResultatsPublies(false);
            e.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            e.setPromotions(new HashSet<>(Set.of(dic2())));
            e.addQuestion(new QuestionOuverte("Q", 1, 2.0));
            return evaluationRepository.save(e).getId();
        });
    }

    private Promotion dic2() {
        return promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
    }

    private Long nouvelEtudiant() {
        String email = "etu-pg-" + UUID.randomUUID() + "@ept.sn";
        return utilisateurRepository.save(new Etudiant("Test", "Pg", email, "motDePasse", "ETU-" + UUID.randomUUID(), dic2())).getId();
    }
}
