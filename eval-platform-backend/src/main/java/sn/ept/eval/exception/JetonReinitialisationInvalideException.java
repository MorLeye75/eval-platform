package sn.ept.eval.exception;

/** Lien de réinitialisation inconnu, expiré, déjà utilisé ou remplacé par une demande plus récente (400 JETON_INVALIDE). */
public class JetonReinitialisationInvalideException extends RuntimeException {

    public JetonReinitialisationInvalideException() {
        super("Ce lien de réinitialisation est invalide ou a expiré. Faites une nouvelle demande.");
    }
}
