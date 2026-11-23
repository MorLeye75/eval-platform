package sn.ept.eval.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/**
 * Envoi par SMTP, en arrière-plan : la réponse HTTP ne dépend ni de la lenteur du serveur de messagerie ni de
 * l'existence du compte. Un échec est journalisé ; l'utilisateur peut refaire une demande.
 */
public class EnvoiEmailSmtp implements EnvoiEmail {

    private static final Logger log = LoggerFactory.getLogger(EnvoiEmailSmtp.class);

    private final JavaMailSender mailSender;
    private final String expediteur;

    public EnvoiEmailSmtp(JavaMailSender mailSender, String expediteur) {
        this.mailSender = mailSender;
        this.expediteur = expediteur;
    }

    @Override
    @Async(EmailConfig.EXECUTEUR)
    public void envoyerLienReinitialisation(String destinataire, String prenom, String lien) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject("Réinitialisation de votre mot de passe");
        message.setText("Bonjour " + prenom + ",\n\n"
                + "Une réinitialisation du mot de passe de votre compte a été demandée. Pour choisir un nouveau mot de "
                + "passe, ouvrez ce lien (valable 30 minutes, une seule fois) :\n\n"
                + lien + "\n\n"
                + "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe reste inchangé.\n");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("[E-mail] Lien de réinitialisation non envoyé à {} : {}", destinataire, e.getMessage());
        }
    }
}
