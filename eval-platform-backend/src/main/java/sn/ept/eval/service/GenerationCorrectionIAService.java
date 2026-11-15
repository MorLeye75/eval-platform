package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.correction.GenerationResponse;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.ReponseOuverteRepository;

import java.util.List;

/** Déclenchement de la génération des propositions IA d'une évaluation terminée (DS-08, section A). */
@Service
public class GenerationCorrectionIAService {

    private final EvaluationService evaluationService;
    private final ReponseOuverteRepository reponseOuverteRepository;
    private final CorrectionIATache tache;

    public GenerationCorrectionIAService(EvaluationService evaluationService, ReponseOuverteRepository reponseOuverteRepository,
                                         CorrectionIATache tache) {
        this.evaluationService = evaluationService;
        this.reponseOuverteRepository = reponseOuverteRepository;
        this.tache = tache;
    }

    @Transactional(readOnly = true)
    public GenerationResponse generer(Long evaluationId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);
        if (evaluation.getStatut() != StatutEvaluation.TERMINEE) {
            throw new EvaluationNonModifiableException(
                    "Les propositions IA ne peuvent être générées qu'une fois l'évaluation terminée : toutes les copies ne sont pas remises.");
        }
        List<Long> ids = reponseOuverteRepository.idsAProposer(evaluationId);
        // Idempotent : seules les réponses sans proposition sont reprises, et la tâche revérifie avant d'écrire
        ids.forEach(tache::proposerAsync);
        return new GenerationResponse(evaluationId, ids.size());
    }
}
