package sn.ept.eval.model.enums;

/**
 * Verdict d'un cas de test, déduit du statut Judge0 (DS-12). Seule information montrée à l'étudiant pour un
 * cas masqué (DS-13) : elle aide à comprendre l'échec sans rien dévoiler du cas.
 */
public enum VerdictCas {
    /** Statut 3 « Accepted ». */
    REUSSI,
    /** Statut 4 « Wrong Answer ». */
    MAUVAISE_REPONSE,
    /** Statut 5 « Time Limit Exceeded ». */
    TEMPS_DEPASSE,
    /** Statut 6 « Compilation Error ». */
    ERREUR_COMPILATION,
    /** Statuts 7 à 12 : erreurs à l'exécution (signal, dépassement mémoire, exception…). */
    ERREUR_EXECUTION
}
