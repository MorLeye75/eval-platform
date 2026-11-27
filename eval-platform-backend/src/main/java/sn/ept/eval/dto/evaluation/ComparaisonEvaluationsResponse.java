package sn.ept.eval.dto.evaluation;

import sn.ept.eval.model.enums.StatutEvaluation;

import java.util.List;

/**
 * Comparaison de plusieurs évaluations d'un même enseignant (DS-19). Notes ramenées sur 20 ; un indicateur vaut
 * {@code null} (jamais 0) tant qu'aucune copie de l'évaluation n'est notée.
 */
public record ComparaisonEvaluationsResponse(List<Ligne> evaluations) {

    public record Ligne(Long evaluationId, String titre, StatutEvaluation statut, long nbAttendus, long nbNotees,
                        Double moyenneSur20, Double medianeSur20, Double tauxReussite) {}
}
