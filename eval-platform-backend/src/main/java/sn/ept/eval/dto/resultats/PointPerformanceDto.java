package sn.ept.eval.dto.resultats;

import java.time.LocalDateTime;

/**
 * Une évaluation dans le tableau de bord de l'étudiant, notes ramenées sur 20.
 *
 * @param moyennePromotionSur20 null en dessous de 5 copies notées (anonymat)
 */
public record PointPerformanceDto(Long soumissionId, Long evaluationId, String titre, LocalDateTime date,
                                  double noteSur20, Double moyennePromotionSur20) {}
