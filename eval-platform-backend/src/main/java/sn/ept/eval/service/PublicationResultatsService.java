package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.ResultatsPublicationResponse;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.time.LocalDateTime;

/**
 * Publication des résultats d'une évaluation par son enseignant (DS-07) : l'autre moitié de « publication et
 * consultation des résultats », dont l'étudiant voit la suite en DS-13 (une copie n'est consultable que si elle est
 * notée ET que {@code resultatsPublies} est vrai).
 *
 * <p>Conditions : évaluation terminée et marge de tolérance écoulée (plus personne ne compose, donc les bonnes
 * réponses peuvent être dévoilées), résultats pas déjà publiés. La bascule est une mise à jour conditionnelle :
 * deux clics simultanés ne notifient pas deux fois.
 */
@Service
@Transactional
public class PublicationResultatsService {

    private final EvaluationService evaluationService;
    private final EvaluationRepository evaluationRepository;
    private final SoumissionRepository soumissionRepository;
    private final NotificationService notificationService;
    private final TempsEpreuveService tempsEpreuveService;

    public PublicationResultatsService(EvaluationService evaluationService,
                                       EvaluationRepository evaluationRepository,
                                       SoumissionRepository soumissionRepository,
                                       NotificationService notificationService,
                                       TempsEpreuveService tempsEpreuveService) {
        this.evaluationService = evaluationService;
        this.evaluationRepository = evaluationRepository;
        this.soumissionRepository = soumissionRepository;
        this.notificationService = notificationService;
        this.tempsEpreuveService = tempsEpreuveService;
    }

    public ResultatsPublicationResponse publier(Long evaluationId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);

        if (Boolean.TRUE.equals(evaluation.getResultatsPublies())) {
            throw dejaPublies();
        }
        if (evaluation.getStatut() != StatutEvaluation.TERMINEE) {
            throw new EvaluationNonModifiableException(
                    "Les résultats ne peuvent être publiés qu'une fois l'évaluation terminée.");
        }
        // Plus personne ne compose après la fermeture + la marge : c'est aussi la règle de DS-13 pour les corrigés
        LocalDateTime finDeLaMarge = evaluation.getDateFermeture().plusMinutes(TempsEpreuveService.MARGE_TOLERANCE_MINUTES);
        if (tempsEpreuveService.maintenant().isBefore(finDeLaMarge)) {
            throw new EvaluationNonModifiableException(
                    "Des étudiants peuvent encore rendre leur copie : réessayez après la marge de tolérance de "
                            + TempsEpreuveService.MARGE_TOLERANCE_MINUTES + " minutes suivant la fermeture.");
        }

        // Bascule atomique : 0 ligne = un autre appel vient de publier (pas de notification en double)
        if (evaluationRepository.publierResultatsSiNonPublies(evaluationId) == 0) {
            throw dejaPublies();
        }
        // Relue : l'entité chargée avant la mise à jour est périmée (clearAutomatically)
        Evaluation publiee = evaluationRepository.findById(evaluationId).orElseThrow();
        notificationService.resultatsPublies(publiee);

        long total = soumissionRepository.countByEvaluationId(evaluationId);
        long notees = soumissionRepository.countByEvaluationIdAndStatut(evaluationId, StatutSoumission.EVALUE);
        return new ResultatsPublicationResponse(evaluationId, true, total, notees, total - notees);
    }

    private EvaluationNonModifiableException dejaPublies() {
        return new EvaluationNonModifiableException("Les résultats de cette évaluation sont déjà publiés.");
    }
}
