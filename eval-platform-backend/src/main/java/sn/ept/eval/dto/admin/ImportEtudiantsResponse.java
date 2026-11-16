package sn.ept.eval.dto.admin;

import java.util.List;

/**
 * Résultat d'un import CSV : tout ou rien. Si {@code erreurs} n'est pas vide, rien n'a été créé ({@code importe} faux).
 * {@code comptes} porte les mots de passe provisoires, renvoyés une seule fois.
 */
public record ImportEtudiantsResponse(boolean importe, int nbCrees, List<LigneErreur> erreurs, List<CompteImporte> comptes) {

    /** {@code ligne} : numéro de ligne du fichier (l'en-tête est la ligne 1). */
    public record LigneErreur(int ligne, String message) {}

    public record CompteImporte(int ligne, String email, String motDePasseProvisoire) {}
}
