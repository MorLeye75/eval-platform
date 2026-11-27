package sn.ept.eval.dto.resultats;

import sn.ept.eval.model.enums.VerdictCas;

/**
 * Résultat d'un cas de test pour l'étudiant. Cas masqué : verdict et temps seulement, jamais l'entrée,
 * la sortie attendue, la sortie obtenue ni le message (DS-13) ; et rien avant la fermeture + 2 min.
 *
 * @param reussi  null pour un cas masqué avant la fermeture + 2 min
 * @param verdict null pour un cas masqué avant la fermeture + 2 min, ou pour une correction antérieure à V8
 */
public record CasTestResultatDto(boolean masque, Boolean reussi, VerdictCas verdict, Double tempsExecution,
                                 String entree, String sortieAttendue, String sortieObtenue, String message) {}
