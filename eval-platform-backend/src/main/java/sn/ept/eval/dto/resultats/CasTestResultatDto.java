package sn.ept.eval.dto.resultats;

import sn.ept.eval.model.enums.VerdictCas;

/**
 * Résultat d'un cas de test pour l'étudiant. Cas masqué : verdict et temps seulement, jamais l'entrée,
 * la sortie attendue, la sortie obtenue ni le message (DS-13).
 *
 * @param verdict null pour une correction antérieure à V8 ({@code reussi} reste renseigné)
 */
public record CasTestResultatDto(boolean masque, boolean reussi, VerdictCas verdict, Double tempsExecution,
                                 String entree, String sortieAttendue, String sortieObtenue, String message) {}
