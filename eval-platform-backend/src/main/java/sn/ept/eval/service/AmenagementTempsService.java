package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.AmenagementResponse;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.AmenagementTemps;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.AmenagementTempsRepository;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.SoumissionRepository;

/**
 * Tiers-temps d'un étudiant pour une évaluation (DS-06).
 * TempsEpreuveService relit l'aménagement à chaque calcul : le modifier après le début de la copie
 * changerait le temps d'une copie en cours, il est donc verrouillé dès que la copie existe.
 */
@Service
@Transactional
public class AmenagementTempsService {

    private final EvaluationService evaluationService;
    private final EtudiantRepository etudiantRepository;
    private final AmenagementTempsRepository amenagementTempsRepository;
    private final SoumissionRepository soumissionRepository;

    public AmenagementTempsService(EvaluationService evaluationService,
                                   EtudiantRepository etudiantRepository,
                                   AmenagementTempsRepository amenagementTempsRepository,
                                   SoumissionRepository soumissionRepository) {
        this.evaluationService = evaluationService;
        this.etudiantRepository = etudiantRepository;
        this.amenagementTempsRepository = amenagementTempsRepository;
        this.soumissionRepository = soumissionRepository;
    }

    public AmenagementResponse definir(Long evaluationId, Long etudiantId, int dureeMinutes, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        // Évaluation verrouillée (FOR UPDATE) : un démarrage de copie simultané attend (DS-10 prend le verrou partagé)
        Evaluation evaluation = evaluationService.evaluationVerrouilleeDeLEnseignant(evaluationId, enseignant);
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Étudiant introuvable (ID: " + etudiantId + ")"));

        verifierEvaluationModifiable(evaluation);
        boolean dansUnePromotion = etudiant.getPromotion() != null && evaluation.getPromotions().stream()
                .anyMatch(p -> p.getId().equals(etudiant.getPromotion().getId()));
        if (!dansUnePromotion) {
            throw new IllegalArgumentException("Cet étudiant n'appartient à aucune promotion concernée par l'évaluation.");
        }
        if (dureeMinutes < evaluation.getDureeStandard()) {
            throw new IllegalArgumentException("Un aménagement ne peut qu'augmenter la durée (minimum : "
                    + evaluation.getDureeStandard() + " minutes).");
        }
        // La fin de l'épreuve est plafonnée à la fermeture : un tiers-temps plus long que la plage serait sans effet
        long plage = EvaluationService.plageEnMinutes(evaluation);
        if (dureeMinutes > plage) {
            throw new IllegalArgumentException("La plage d'ouverture (" + plage + " min) est plus courte que la durée aménagée ("
                    + dureeMinutes + " min) : allongez la fermeture ou réduisez le tiers-temps.");
        }
        verifierCopieNonCommencee(evaluation, etudiant);

        AmenagementTemps amenagement = amenagementTempsRepository
                .findByEtudiantIdAndEvaluationId(etudiantId, evaluationId)
                .orElseGet(() -> new AmenagementTemps(etudiant, evaluation, dureeMinutes));
        amenagement.setDureePersonnalisee(dureeMinutes);
        amenagementTempsRepository.save(amenagement);
        return new AmenagementResponse(etudiantId, dureeMinutes);
    }

    public void supprimer(Long evaluationId, Long etudiantId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationVerrouilleeDeLEnseignant(evaluationId, enseignant);
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Étudiant introuvable (ID: " + etudiantId + ")"));

        verifierEvaluationModifiable(evaluation);
        AmenagementTemps amenagement = amenagementTempsRepository
                .findByEtudiantIdAndEvaluationId(etudiantId, evaluationId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Aucun aménagement pour cet étudiant sur cette évaluation."));
        verifierCopieNonCommencee(evaluation, etudiant);
        amenagementTempsRepository.delete(amenagement);
    }

    private void verifierEvaluationModifiable(Evaluation evaluation) {
        if (evaluation.getStatut() == StatutEvaluation.TERMINEE) {
            throw new EvaluationNonModifiableException("L'évaluation est terminée : les aménagements ne sont plus modifiables.");
        }
    }

    private void verifierCopieNonCommencee(Evaluation evaluation, Etudiant etudiant) {
        if (soumissionRepository.existsByEvaluationIdAndEtudiantId(evaluation.getId(), etudiant.getId())) {
            throw new EvaluationNonModifiableException("L'étudiant a déjà commencé : son aménagement est verrouillé.");
        }
    }
}
