package sn.ept.eval.service.judge0;

/**
 * Judge0 n'a pas pu exécuter le code : panne, surcharge, délai dépassé, erreur interne (statuts 13 et 14).
 * Ce n'est pas une erreur de l'étudiant : la correction sera retentée plus tard (DS-12).
 */
public class Judge0IndisponibleException extends Exception {

    public Judge0IndisponibleException(String message) {
        super(message);
    }

    public Judge0IndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
