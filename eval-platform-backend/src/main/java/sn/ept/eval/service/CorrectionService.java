package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.SoumissionRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class CorrectionService {

    private static final Logger log = LoggerFactory.getLogger(CorrectionService.class);

    private final SoumissionRepository soumissionRepository;
    /** Transaction propre à chaque correction : toute erreur l'annule entièrement (pas de notes partielles). */
    private final TransactionTemplate transaction;

    public CorrectionService(SoumissionRepository soumissionRepository, PlatformTransactionManager transactionManager) {
        this.soumissionRepository = soumissionRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        // Nouvelle transaction même si l'appel arrive pendant la fin d'une autre (afterCommit d'une remise)
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Correction asynchrone en arrière-plan déclenchée dès la soumission de la copie
     * (conforme au Diagramme de Séquence 1).
     *
     * En cas d'erreur, la transaction est annulée : aucune note n'est enregistrée et la copie reste SOUMIS,
     * reprise par la relance planifiée (ClotureCopiesService.relancerCorrectionsBloquees).
     */
    @Async("correctionTaskExecutor")
    public CompletableFuture<Void> corrigerSoumissionAsync(Long soumissionId) {
        log.info("[Async-Correction] Démarrage de la correction pour la soumission ID={}", soumissionId);
        try {
            transaction.executeWithoutResult(tx -> corriger(soumissionId));
        } catch (RuntimeException e) {
            log.error("[Async-Correction] Correction de la soumission ID={} annulée (rien n'est enregistré), "
                    + "nouvelle tentative par la relance planifiée", soumissionId, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void corriger(Long soumissionId) {
        // Verrou : deux déclenchements simultanés (soumission et clôture automatique, relance) sont sérialisés
        Soumission soumission = soumissionRepository.findByIdForUpdate(soumissionId).orElse(null);
        if (soumission == null) {
            log.warn("[Async-Correction] Soumission ID={} introuvable", soumissionId);
            return;
        }
        // Idempotence : une copie déjà corrigée (ou rouverte) n'est pas recorrigée, la note de l'enseignant est conservée
        if (soumission.getStatut() != StatutSoumission.SOUMIS) {
            log.info("[Async-Correction] Soumission ID={} ignorée (statut {})", soumissionId, soumission.getStatut());
            return;
        }

        for (ReponseEtudiant reponse : soumission.getReponses()) {
            // La question est un proxy LAZY de Question (classe abstraite) : il faut le déballer avant le cast
            Question question = (Question) Hibernate.unproxy(reponse.getQuestion());

            if (question instanceof QuestionQCM questionQcm && reponse instanceof ReponseQCM reponseQCM) {
                corrigerQuestionQcm(reponseQCM, questionQcm);
            }
        }

        // Copie entièrement corrigeable automatiquement seulement si TOUTES les questions de l'évaluation
        // sont des QCM (une question ouverte ou de code sans réponse exige quand même l'enseignant)
        boolean toutesQuestionsSontQcm = soumission.getEvaluation().getQuestions().stream()
                .allMatch(q -> Hibernate.unproxy(q) instanceof QuestionQCM); // QCM et quiz chronométrés

        if (toutesQuestionsSontQcm) {
            double note = soumission.getReponses().stream()
                    .filter(r -> r.getNoteObtenue() != null)
                    .mapToDouble(ReponseEtudiant::getNoteObtenue)
                    .sum();
            soumission.setNoteFinale(note);
            soumission.setStatut(StatutSoumission.EVALUE);
        } else {
            soumission.setStatut(StatutSoumission.EN_CORRECTION);
        }

        soumissionRepository.save(soumission);
        log.info("[Async-Correction] Correction terminée pour soumission ID={}, statut={}",
                soumissionId, soumission.getStatut());
    }

    private void corrigerQuestionQcm(ReponseQCM reponseQCM, QuestionQCM questionQcm) {
        Set<Long> idsCorrects = questionQcm.getChoix().stream()
                .filter(ChoixReponse::getEstCorrect)
                .map(ChoixReponse::getId)
                .collect(Collectors.toSet());

        Set<Long> idsCoches = reponseQCM.getChoixCoches().stream()
                .map(ChoixReponse::getId)
                .collect(Collectors.toSet());

        if (idsCorrects.equals(idsCoches)) {
            reponseQCM.setNoteObtenue(questionQcm.getPoints());
        } else {
            reponseQCM.setNoteObtenue(0.0);
        }

        reponseQCM.setValideeParProf(true);
        reponseQCM.setCommentaireProf("Correction automatique QCM.");
    }
}
