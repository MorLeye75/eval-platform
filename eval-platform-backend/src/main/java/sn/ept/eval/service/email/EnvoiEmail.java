package sn.ept.eval.service.email;

/**
 * Envoi des e-mails de la plateforme. Avec un serveur SMTP configuré ({@code SPRING_MAIL_HOST}), {@link EnvoiEmailSmtp} ;
 * sinon {@link EnvoiEmailJournal} écrit le lien dans les logs (développement, démonstration).
 * <p>
 * L'envoi ne doit jamais faire échouer la requête ni révéler, par sa durée, qu'un compte existe : l'implémentation
 * SMTP envoie en arrière-plan et ne fait que journaliser ses échecs.
 */
public interface EnvoiEmail {

    void envoyerLienReinitialisation(String destinataire, String prenom, String lien);
}
