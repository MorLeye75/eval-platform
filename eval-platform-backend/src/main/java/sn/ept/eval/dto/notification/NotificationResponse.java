package sn.ept.eval.dto.notification;

import sn.ept.eval.model.enums.TypeNotification;

import java.time.LocalDateTime;

/**
 * Une notification de l'utilisateur connecté (DS-15).
 *
 * @param evaluationId évaluation concernée, nulle si aucune
 * @param soumissionId copie concernée : le front ouvre directement ses résultats (/etudiant/resultats/{id})
 */
public record NotificationResponse(Long id, TypeNotification type, String message, boolean lue,
                                   LocalDateTime dateEnvoi, Long evaluationId, Long soumissionId) {
}
