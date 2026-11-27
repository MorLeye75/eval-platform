package sn.ept.eval.dto.resultats;

/**
 * Choix d'une question QCM dans les résultats.
 *
 * @param estCorrect bonne réponse ou non ; null tant que le corrigé n'est pas visible (avant la fermeture)
 */
public record ChoixResultatDto(Long id, String texte, boolean coche, Boolean estCorrect) {}
