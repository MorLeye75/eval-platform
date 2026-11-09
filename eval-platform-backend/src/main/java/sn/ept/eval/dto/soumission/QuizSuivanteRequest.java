package sn.ept.eval.dto.soumission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Passage à la question suivante d'un quiz.
 *
 * @param indexActuel question sur laquelle le navigateur se trouve : le serveur n'avance que s'il y est aussi
 */
public record QuizSuivanteRequest(@NotNull @PositiveOrZero Integer indexActuel) {
}
