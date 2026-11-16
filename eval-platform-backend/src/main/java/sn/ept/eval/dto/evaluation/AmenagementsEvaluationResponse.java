package sn.ept.eval.dto.evaluation;

import java.util.List;

/**
 * Étudiants attendus à une évaluation et leur tiers-temps éventuel (DS-06). {@code dureeMinutes} vaut {@code null}
 * sans aménagement (l'étudiant a alors la durée standard) ; {@code modifiable} reprend les règles du PUT et du DELETE
 * (évaluation non terminée et copie non commencée) pour que l'écran ne propose que ce que le serveur acceptera.
 */
public record AmenagementsEvaluationResponse(
        Long evaluationId, int dureeStandard, long plageMinutes, List<EtudiantAmenage> etudiants) {

    public record EtudiantAmenage(Long etudiantId, String nom, String prenom, String numEtudiant,
                                  Integer dureeMinutes, boolean modifiable) {}
}
