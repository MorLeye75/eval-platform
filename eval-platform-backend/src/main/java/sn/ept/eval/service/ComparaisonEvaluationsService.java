package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.ComparaisonEvaluationsResponse;
import sn.ept.eval.dto.evaluation.StatistiquesEvaluationResponse;

import java.util.HashSet;
import java.util.List;

/**
 * Comparaison de 2 à 6 évaluations de l'enseignant connecté (DS-19). Aucun calcul propre : chaque ligne vient de
 * {@link StatistiquesEvaluationService} (DS-09), qui vérifie le propriétaire et l'ouverture de l'évaluation. Une
 * évaluation refusée fait refuser toute la comparaison.
 */
@Service
@Transactional(readOnly = true)
public class ComparaisonEvaluationsService {

    static final int MIN = 2;
    static final int MAX = 6;

    private final StatistiquesEvaluationService statistiquesService;

    public ComparaisonEvaluationsService(StatistiquesEvaluationService statistiquesService) {
        this.statistiquesService = statistiquesService;
    }

    public ComparaisonEvaluationsResponse comparer(List<Long> ids, String email) {
        if (ids == null || ids.size() < MIN || ids.size() > MAX) {
            throw new IllegalArgumentException("Choisissez entre " + MIN + " et " + MAX + " évaluations à comparer.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Une évaluation ne peut être comparée qu'une fois.");
        }
        return new ComparaisonEvaluationsResponse(ids.stream().map(id -> {
            StatistiquesEvaluationResponse s = statistiquesService.statistiques(id, email);
            return new ComparaisonEvaluationsResponse.Ligne(s.evaluationId(), s.titre(), s.statut(), s.nbAttendus(), s.nbNotees(),
                    s.moyenneSur20(), s.medianeSur20(), s.tauxReussite());
        }).toList());
    }
}
