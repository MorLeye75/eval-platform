package sn.ept.eval.dto.resultats;

import java.util.List;

/**
 * Tableau de bord de l'étudiant (GET /etudiant/performances, DS-13) : évaluations notées et publiées, de la plus
 * ancienne à la plus récente, notes sur 20.
 *
 * @param moyenneGenerale          moyenne de l'étudiant sur toutes ces évaluations, null s'il n'y en a aucune
 * @param moyennePromotionGenerale moyenne de la promotion sur les évaluations où elle est connue (5 copies et plus)
 * @param ecartMoyenne             moyenne de l'étudiant moins celle de la promotion, sur ces mêmes évaluations
 *                                 seulement (comparaison à périmètre égal) ; null si aucune n'est comparable
 */
public record PerformancesResponse(List<PointPerformanceDto> historique, Double moyenneGenerale,
                                   Double moyennePromotionGenerale, Double ecartMoyenne, int nbEvaluations) {}
