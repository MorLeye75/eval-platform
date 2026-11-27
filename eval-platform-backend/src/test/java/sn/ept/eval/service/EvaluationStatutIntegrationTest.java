package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.QuestionOuverte;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * DS-05 sur H2, avec les vraies mises à jour conditionnelles : transitions, absence de notification en double
 * (y compris pour deux passages simultanés) et version incrémentée.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:statuts;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class EvaluationStatutIntegrationTest {

    @Autowired private EvaluationStatutService statutService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("PUBLIEE ouverte → EN_COURS avec une notification ; un second passage ne notifie plus")
    void ouverture_UneSeuleNotification() {
        Long id = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(2));
        Long versionAvant = evaluationRepository.findById(id).orElseThrow().getVersion();

        statutService.mettreAJourStatuts();
        statutService.mettreAJourStatuts();

        Evaluation apres = evaluationRepository.findById(id).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutEvaluation.EN_COURS);
        assertThat(apres.getVersion()).isGreaterThan(versionAvant); // « update versioned » : la version avance
        verify(notificationService, times(1)).evaluationOuverte(argThat(e -> e.getId().equals(id)));
    }

    @Test
    @DisplayName("La notification reçoit l'évaluation relue après la mise à jour, avec ses promotions chargeables")
    void notificationRecoitUneEvaluationFraiche() {
        Long id = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(2));
        org.mockito.Mockito.doAnswer(invocation -> {
            Evaluation e = invocation.getArgument(0);
            assertThat(e.getStatut()).isEqualTo(StatutEvaluation.EN_COURS);
            assertThat(e.getPromotions()).hasSize(1); // chargement paresseux possible : appel dans la transaction
            return null;
        }).when(notificationService).evaluationOuverte(argThat(e -> e.getId().equals(id)));

        statutService.mettreAJourStatuts();

        verify(notificationService).evaluationOuverte(argThat(e -> e.getId().equals(id)));
    }

    @Test
    @DisplayName("Deux passages simultanés de la tâche : une seule notification (mise à jour conditionnelle atomique)")
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
            var f1 = pool.submit(passage);
            var f2 = pool.submit(passage);
            depart.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(evaluationRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.EN_COURS);
        verify(notificationService, times(1)).evaluationOuverte(argThat(e -> e.getId().equals(id)));
    }

    @Test
    @DisplayName("PUBLIEE déjà fermée → TERMINEE directement, jamais EN_COURS, sans notification « ouverte »")
    void publieeDejaFermee() {
        Long id = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));

        statutService.mettreAJourStatuts();

        assertThat(evaluationRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.TERMINEE);
        verify(notificationService, never()).evaluationOuverte(argThat(e -> e.getId().equals(id)));
    }

    @Test
    @DisplayName("EN_COURS dont la fermeture est passée → TERMINEE ; un brouillon et une évaluation à venir ne bougent pas")
    void fermetureEtEvaluationsIntactes() {
        Long enCours = evaluation(StatutEvaluation.EN_COURS, LocalDateTime.now().minusHours(3), LocalDateTime.now().minusMinutes(1));
        Long brouillon = evaluation(StatutEvaluation.BROUILLON, LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));
        Long aVenir = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));

        statutService.mettreAJourStatuts();

        assertThat(evaluationRepository.findById(enCours).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.TERMINEE);
        assertThat(evaluationRepository.findById(brouillon).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.BROUILLON);
        assertThat(evaluationRepository.findById(aVenir).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.PUBLIEE);
        // Les évaluations de démonstration du jeu de données ne comptent pas : on ne regarde que celles du test
        verify(notificationService, never()).evaluationOuverte(argThat(e -> java.util.List.of(enCours, brouillon, aVenir).contains(e.getId())));
    }

    @Test
    @DisplayName("Échec de la notification : la transition est annulée avec elle et reprise au passage suivant")
    void echecNotification_TransitionAnnulee() {
        Long id = evaluation(StatutEvaluation.PUBLIEE, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(2));
        org.mockito.Mockito.doThrow(new IllegalStateException("notification impossible"))
                .doNothing()
                .when(notificationService).evaluationOuverte(argThat(e -> e.getId().equals(id)));

        statutService.mettreAJourStatuts(); // échec isolé : ni statut ni notification validés
        assertThat(evaluationRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.PUBLIEE);

        statutService.mettreAJourStatuts(); // reprise
        assertThat(evaluationRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutEvaluation.EN_COURS);
    }

    private Long evaluation(StatutEvaluation statut, LocalDateTime ouverture, LocalDateTime fermeture) {
        return transactionTemplate.execute(tx -> {
            Enseignant prof = (Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow();
            Evaluation e = new Evaluation();
            e.setTitre("Statut " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(ouverture);
            e.setDateFermeture(fermeture);
            e.setStatut(statut);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotionRepository.findByNom("DIC2 Informatique").orElseThrow())));
            e.addQuestion(new QuestionOuverte("Q", 1, 2.0));
            return evaluationRepository.save(e).getId();
        });
    }
}
