package sn.ept.eval.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sans serveur SMTP : le lien est écrit dans les logs, pour pouvoir dérouler la démonstration en local.
 * En production, configurer un serveur SMTP ({@code SPRING_MAIL_HOST}...) : le lien donne accès au compte.
 */
public class EnvoiEmailJournal implements EnvoiEmail {

    private static final Logger log = LoggerFactory.getLogger(EnvoiEmailJournal.class);

    @Override
    public void envoyerLienReinitialisation(String destinataire, String prenom, String lien) {
        log.warn("[E-mail non envoyé : aucun serveur SMTP configuré] Lien de réinitialisation pour {} : {}", destinataire, lien);
    }
}
