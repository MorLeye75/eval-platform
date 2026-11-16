package sn.ept.eval.dto.evaluation;

import sn.ept.eval.model.enums.StatutEvaluation;

import java.util.List;

/**
 * Statistiques d'une évaluation (DS-09), calculées sur les copies notées (EVALUE) et ramenées sur 20. Tous les
 * indicateurs valent {@code null} (jamais 0) tant qu'aucune copie n'est notée ; l'écart-type exige 2 copies notées.
 */
public record StatistiquesEvaluationResponse(
        Long evaluationId, String titre, StatutEvaluation statut, double bareme,
        long nbAttendus, long nbCopies, long nbNotees, long nbEnCorrection,
        Double moyenneSur20, Double medianeSur20, Double minSur20, Double maxSur20, Double ecartTypeSur20,
        Double tauxReussite, List<Tranche> distribution, List<QuestionStat> questions) {

    /** Tranche [de ; a[ sur 20 (la dernière inclut 20). */
    public record Tranche(int de, int a, long nb) {}

    /** {@code tauxReussite} : points obtenus / points possibles pour cette question, en %, sur les copies notées. */
    public record QuestionStat(Long questionId, int ordre, String enonce, double points, Double moyennePoints, Double tauxReussite) {}
}
