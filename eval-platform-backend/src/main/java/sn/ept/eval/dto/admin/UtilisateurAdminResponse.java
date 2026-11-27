package sn.ept.eval.dto.admin;

import sn.ept.eval.model.enums.Role;

import java.time.LocalDateTime;

/** Compte vu par l'administrateur. Jamais de mot de passe ni de haché. */
public record UtilisateurAdminResponse(
        Long id, String nom, String prenom, String email, Role role, boolean actif,
        LocalDateTime dateCreation, LocalDateTime derniereConnexion,
        Long promotionId, String promotion, String numEtudiant, String matricule) {}
