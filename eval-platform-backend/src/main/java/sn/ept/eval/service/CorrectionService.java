package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.TypeQuestion;
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

    public CorrectionService(SoumissionRepository soumissionRepository) {
        this.soumissionRepository = soumissionRepository;
    }

    /**
     * Correction asynchrone en arrière-plan déclenchée dès la soumission de la copie
     * (conforme au Diagramme de Séquence 1).
     */
    @Async("correctionTaskExecutor")
    @Transactional
    public CompletableFuture<Void> corrigerSoumissionAsync(Long soumissionId) {
        log.info("[Async-Correction] Démarrage de la correction pour la soumission ID={}", soumissionId);

        try {
            Soumission soumission = soumissionRepository.findById(soumissionId).orElse(null);
            if (soumission == null) {
                log.warn("[Async-Correction] Soumission ID={} introuvable", soumissionId);
                return CompletableFuture.completedFuture(null);
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
                    .allMatch(q -> q.getType() == TypeQuestion.QCM);

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

        } catch (Exception e) {
            log.error("[Async-Correction] Erreur lors de la correction de la soumission ID={}", soumissionId, e);
        }

        return CompletableFuture.completedFuture(null);
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
