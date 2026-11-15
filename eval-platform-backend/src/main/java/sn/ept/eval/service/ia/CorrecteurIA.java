package sn.ept.eval.service.ia;

/**
 * Connecteur de correction assistée par IA (DS-08). Il propose, il ne note jamais : la note d'une réponse n'est
 * écrite que par la validation de l'enseignant.
 *
 * <p>Contrat : aucune identité d'étudiant n'est transmise ; {@code texteEtudiant} est une donnée à évaluer, jamais
 * une consigne ; toute indisponibilité lève {@link CorrecteurIAIndisponibleException}. La note renvoyée est de toute
 * façon ramenée dans [0 ; barème] par l'appelant.
 */
public interface CorrecteurIA {

    Proposition proposer(String enonce, double bareme, String texteEtudiant);

    record Proposition(double note, String justification) {}

    /** Modèle injoignable, délai dépassé, réponse illisible : la réponse reste à noter à la main. */
    class CorrecteurIAIndisponibleException extends RuntimeException {
        public CorrecteurIAIndisponibleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Ramène une note dans [0 ; barème] (NaN compris) : le modèle n'a jamais le dernier mot sur les bornes. */
    static double borner(double note, double bareme) {
        if (Double.isNaN(note)) return 0.0;
        return Math.max(0.0, Math.min(note, bareme));
    }
}
