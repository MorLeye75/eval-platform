package sn.ept.eval.dto.evaluation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notes par étudiant attendu pour l'enseignant (DS-09, section C). {@code etat} : ABSENT sans copie, sinon le statut de la
 * copie ; {@code note} et {@code noteSurVingt} valent {@code null} (jamais 0) tant que la copie n'est pas notée. Pas d'e-mail :
 * il reste dans l'export CSV seul.
 */
public record ResultatsEvaluationResponse(Long evaluationId, double bareme, List<LigneResultat> lignes) {

    public record LigneResultat(Long etudiantId, String numEtudiant, String nom, String prenom, String etat,
                                Double note, Double noteSurVingt, LocalDateTime dateRemise) {}
}
