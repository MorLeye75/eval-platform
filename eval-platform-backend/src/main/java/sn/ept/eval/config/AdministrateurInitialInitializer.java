package sn.ept.eval.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.Administrateur;
import sn.ept.eval.repository.AdministrateurRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.regex.Pattern;

/**
 * Premier administrateur d'une installation neuve. En production, aucun compte de démonstration n'est créé : sans ce
 * compte, personne ne pourrait se connecter ni créer les autres. S'il n'existe aucun administrateur, il est créé au
 * démarrage depuis {@code ADMIN_INITIAL_EMAIL} et {@code ADMIN_INITIAL_MOT_DE_PASSE}, avec un mot de passe provisoire
 * à changer à la première connexion (DS-16). Dès qu'un administrateur existe, ces variables sont ignorées : les retirer
 * de la configuration après la première connexion.
 *
 * <p>Une configuration incomplète ou trop faible arrête le démarrage avec la raison, plutôt que de créer un compte
 * fragile. Le mot de passe n'est jamais journalisé.
 */
@Component
@Order(0)
public class AdministrateurInitialInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministrateurInitialInitializer.class);
    static final int LONGUEUR_MIN = 12;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AdministrateurRepository administrateurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String motDePasse;

    @Autowired
    public AdministrateurInitialInitializer(AdministrateurRepository administrateurRepository,
                                            UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder,
                                            @Value("${app.admin-initial.email:}") String email,
                                            @Value("${app.admin-initial.mot-de-passe:}") String motDePasse) {
        this.administrateurRepository = administrateurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        this.motDePasse = motDePasse == null ? "" : motDePasse;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (administrateurRepository.count() > 0) {
            if (!email.isEmpty()) {
                log.info("Un administrateur existe déjà : ADMIN_INITIAL_EMAIL est ignoré (à retirer de la configuration).");
            }
            return;
        }
        if (email.isEmpty() && motDePasse.isEmpty()) {
            log.warn("Aucun administrateur : définir ADMIN_INITIAL_EMAIL et ADMIN_INITIAL_MOT_DE_PASSE puis redémarrer, "
                    + "sinon personne ne peut se connecter.");
            return;
        }
        if (email.isEmpty() || !EMAIL.matcher(email).matches()) {
            throw new IllegalStateException("ADMIN_INITIAL_EMAIL manquant ou invalide : adresse du premier administrateur attendue.");
        }
        if (motDePasse.isEmpty()) {
            throw new IllegalStateException("ADMIN_INITIAL_MOT_DE_PASSE manquant : mot de passe provisoire du premier administrateur.");
        }
        if (motDePasse.length() < LONGUEUR_MIN || motDePasse.length() > 72) {
            throw new IllegalStateException("ADMIN_INITIAL_MOT_DE_PASSE doit contenir entre " + LONGUEUR_MIN + " et 72 caractères.");
        }
        if (Boolean.TRUE.equals(utilisateurRepository.existsByEmail(email))) {
            throw new IllegalStateException("L'adresse " + email + " appartient déjà à un compte : choisir une autre "
                    + "ADMIN_INITIAL_EMAIL pour le premier administrateur.");
        }

        Administrateur admin = new Administrateur("Administrateur", "Principal", email, passwordEncoder.encode(motDePasse));
        admin.setMotDePasseProvisoire(true);
        utilisateurRepository.save(admin);
        log.info("Premier administrateur créé : {} (mot de passe provisoire, à changer à la première connexion).", email);
    }
}
