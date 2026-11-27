package sn.ept.eval.dto.admin;

/** Compte créé et son mot de passe provisoire, renvoyé cette seule fois (le serveur n'en garde que le haché). */
public record CompteCreeResponse(UtilisateurAdminResponse utilisateur, String motDePasseProvisoire) {}
