package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.profil.ChangementMotDePasseRequest;
import sn.ept.eval.dto.profil.ProfilResponse;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.repository.UtilisateurRepository;
import sn.ept.eval.security.services.TentativesConnexionService;

import java.nio.charset.StandardCharsets;

/**
 * Profil de l'utilisateur connecté (tous les rôles) : consultation, et changement de son mot de passe.
 * L'identité (nom, prénom, email) n'est modifiable que par l'administrateur (cahier des charges, gestion des comptes).
 */
@Service
public class ProfilService {

    private static final Logger log = LoggerFactory.getLogger(ProfilService.class);

    /** BCrypt ignore tout ce qui dépasse 72 octets (et Spring Security refuse un tel mot de passe). */
    static final int OCTETS_MAX_BCRYPT = 72;

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final TentativesConnexionService tentativesConnexionService;

    public ProfilService(UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder,
                         TentativesConnexionService tentativesConnexionService) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.tentativesConnexionService = tentativesConnexionService;
    }

    @Transactional(readOnly = true)
    public ProfilResponse profil(String email) {
        Utilisateur u = utilisateur(email);
        String numEtudiant = null;
        String promotion = null;
        if (u instanceof Etudiant etudiant) {
            numEtudiant = etudiant.getNumEtudiant();
            promotion = etudiant.getPromotion() != null ? etudiant.getPromotion().getNom() : null;
        }
        return new ProfilResponse(u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getRole(), numEtudiant, promotion);
    }

    /**
     * Les échecs comptent avec ceux de la connexion (même couple email, IP) : un jeton volé ne permet pas de deviner
     * le mot de passe sans limite. Le mot de passe actuel faux donne un 400, pas un 401 : le navigateur
     * déconnecterait l'utilisateur.
     */
    @Transactional
    public void changerMotDePasse(String email, ChangementMotDePasseRequest demande, String ip) {
        tentativesConnexionService.verifierNonBloque(email, ip);
        Utilisateur u = utilisateur(email);
        if (!passwordEncoder.matches(demande.motDePasseActuel(), u.getMotDePasse())) {
            tentativesConnexionService.enregistrerEchec(email, ip);
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect.");
        }
        String nouveau = demande.nouveauMotDePasse();
        if (nouveau.getBytes(StandardCharsets.UTF_8).length > OCTETS_MAX_BCRYPT) {
            throw new IllegalArgumentException("Le nouveau mot de passe est trop long (72 octets au plus ; "
                    + "un caractère accentué en compte deux).");
        }
        if (nouveau.equals(demande.motDePasseActuel())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        u.setMotDePasse(passwordEncoder.encode(nouveau));
        tentativesConnexionService.reinitialiser(email, ip);
        log.info("Mot de passe changé par l'utilisateur ID={}", u.getId());
    }

    private Utilisateur utilisateur(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RessourceNonTrouveeException("Utilisateur introuvable."));
    }
}
