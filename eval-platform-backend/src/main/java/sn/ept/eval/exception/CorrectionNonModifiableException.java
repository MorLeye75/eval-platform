package sn.ept.eval.exception;

/** Note refusée à cause de l'état de la copie ou de la proposition (déjà évaluée, déjà traitée). Traduite en 409 (DS-08). */
public class CorrectionNonModifiableException extends RuntimeException {
    public CorrectionNonModifiableException(String message) {
        super(message);
    }
}
