package sn.ept.eval.dto.soumission;

/**
 * Position de la copie dans un quiz chronométré : seule la question en cours est envoyée au navigateur.
 *
 * @param indexQuestion               index de la question en cours (nbQuestions si le quiz est terminé)
 * @param nbQuestions                 nombre total de questions du quiz
 * @param dureeQuestion               durée de la question en cours, en secondes (null si terminé)
 * @param tempsRestantQuestionSecondes temps restant sur la question en cours
 */
public record EtatQuizDto(int indexQuestion, int nbQuestions, Integer dureeQuestion, long tempsRestantQuestionSecondes) {
}
