package sn.ept.eval.dto.resultats;

/** État d'une copie du point de vue de ses résultats (DS-13). */
public enum EtatResultats {
    /** Copie encore en cours de passation : l'étudiant est renvoyé vers l'épreuve. */
    NON_REMISE,
    /** Copie remise mais résultats non publiés (correction en cours ou publication différée) : aucune note. */
    EN_ATTENTE,
    /** Copie notée et résultats publiés par l'enseignant. */
    PUBLIES
}
