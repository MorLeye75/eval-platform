package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.ept.eval.exception.JetonReinitialisationInvalideException;
import sn.ept.eval.model.JetonReinitialisation;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.repository.JetonReinitialisationRepository;
import sn.ept.eval.repository.UtilisateurRepository;
import sn.ept.eval.service.email.EnvoiEmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Mot de passe oublié (cahier des charges, 4.1) : lien de réinitialisation envoyé par e-mail.
 * <ul>
 *   <li>la demande répond toujours de la même façon : elle ne révèle pas qui a un compte ;</li>
 *   <li>jeton aléatoire de 256 bits, dont seule l'empreinte SHA-256 est stockée ; usage unique, 30 minutes ;
 *       une nouvelle demande invalide les liens précédents ;</li>
 *   <li>au plus 3 demandes par compte en 15 minutes (pas de bombardement de la boîte mail) ;</li>
 *   <li>la réinitialisation coupe les sessions ouvertes et lève un mot de passe provisoire.</li>
 * </ul>
 */
@Service
public class ReinitialisationMotDePasseService {

    private static final Logger log = LoggerFactory.getLogger(ReinitialisationMotDePasseService.class);

    static final Duration VALIDITE = Duration.ofMinutes(30);
    static final Duration FENETRE_LIMITE = Duration.ofMinutes(15);
    static final int DEMANDES_MAX = 3;

    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final JetonReinitialisationRepository jetonRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnvoiEmail envoiEmail;
    private final Clock clock;
    private final String frontendUrl;

    public ReinitialisationMotDePasseService(UtilisateurRepository utilisateurRepository,
                                             JetonReinitialisationRepository jetonRepository,
                                             PasswordEncoder passwordEncoder, EnvoiEmail envoiEmail, Clock clock,
                                             @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.utilisateurRepository = utilisateurRepository;
        this.jetonRepository = jetonRepository;
        this.passwordEncoder = passwordEncoder;
        this.envoiEmail = envoiEmail;
        this.clock = clock;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Ne lève jamais d'exception liée au compte : inconnu, désactivé ou trop sollicité, rien n'est envoyé. */
    @Transactional
    public void demander(String email) {
        Utilisateur u = utilisateurRepository.findByEmail(Utilisateur.normaliserEmail(email)).orElse(null);
        if (u == null || !Boolean.TRUE.equals(u.getActif())) {
            log.info("[Réinitialisation] demande ignorée : compte inconnu ou désactivé");
            return;
        }
        LocalDateTime maintenant = LocalDateTime.now(clock);
        if (jetonRepository.compterDemandesDepuis(u.getId(), maintenant.minus(FENETRE_LIMITE)) >= DEMANDES_MAX) {
            log.warn("[Réinitialisation] demande ignorée pour l'utilisateur ID={} : trop de demandes récentes", u.getId());
            return;
        }
        jetonRepository.invaliderLesLiensDe(u.getId(), maintenant);
        String jeton = nouveauJeton();
        jetonRepository.save(new JetonReinitialisation(u, empreinte(jeton), maintenant, maintenant.plus(VALIDITE)));

        String destinataire = u.getEmail();
        String prenom = u.getPrenom();
        String lien = frontendUrl + "/reset-password/" + jeton;
        // Après la validation : jamais d'e-mail pour un lien qui n'aurait pas été enregistré
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envoiEmail.envoyerLienReinitialisation(destinataire, prenom, lien);
            }
        });
        log.info("[Réinitialisation] lien créé pour l'utilisateur ID={}", u.getId());
    }

    @Transactional
    public void reinitialiser(String jeton, String nouveauMotDePasse) {
        if (nouveauMotDePasse.getBytes(StandardCharsets.UTF_8).length > ProfilService.OCTETS_MAX_BCRYPT) {
            throw new IllegalArgumentException("Le nouveau mot de passe est trop long (72 octets au plus ; "
                    + "un caractère accentué en compte deux).");
        }
        LocalDateTime maintenant = LocalDateTime.now(clock);
        JetonReinitialisation lien = jetonRepository.findByEmpreinteForUpdate(empreinte(jeton))
                .filter(j -> j.estUtilisable(maintenant))
                .orElseThrow(JetonReinitialisationInvalideException::new);
        Utilisateur u = lien.getUtilisateur();
        if (!Boolean.TRUE.equals(u.getActif())) {
            throw new JetonReinitialisationInvalideException();
        }
        lien.setUtiliseLe(maintenant);
        u.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        u.setMotDePasseProvisoire(false);
        // Les jetons de session émis avant sont refusés (AuthTokenFilter) : un appareil volé est déconnecté
        u.setMotDePasseModifieLe(maintenant);
        log.info("[Réinitialisation] mot de passe réinitialisé pour l'utilisateur ID={}", u.getId());
    }

    /** Toutes les heures : un lien expiré est refusé de toute façon. */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purger() {
        int supprimes = jetonRepository.supprimerExpires(LocalDateTime.now(clock));
        if (supprimes > 0) log.info("{} lien(s) de réinitialisation expiré(s) purgé(s)", supprimes);
    }

    private static String nouveauJeton() {
        byte[] octets = new byte[32];
        ALEATOIRE.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    static String empreinte(String jeton) {
        try {
            byte[] hache = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hache);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
