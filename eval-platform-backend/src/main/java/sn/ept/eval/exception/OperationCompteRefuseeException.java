package sn.ept.eval.exception;

/**
 * Opération sur un compte refusée : doublon (e-mail, numéro, matricule), désactivation de son propre compte ou du
 * dernier administrateur actif, changement de promotion d'un étudiant qui a déjà composé. Traduite en 409 (DS-16).
 */
public class OperationCompteRefuseeException extends RuntimeException {
    public OperationCompteRefuseeException(String message) {
        super(message);
    }
}
