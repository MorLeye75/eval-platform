package sn.ept.eval.exception;

/**
 * Action refusée à cause de l'état de l'évaluation (pas un brouillon, copie déjà commencée, terminée...).
 * Traduite en 409 Conflict, comme {@link SoumissionNonModifiableException}.
 */
public class EvaluationNonModifiableException extends RuntimeException {
    public EvaluationNonModifiableException(String message) {
        super(message);
    }
}
