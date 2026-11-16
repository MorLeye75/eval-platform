package sn.ept.eval.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.ept.eval.model.enums.Role;

/** Création d'un compte. promotionId et numEtudiant sont exigés pour un étudiant ; matricule est facultatif (enseignant). */
public record CreerCompteRequest(
        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,
        @NotBlank @Size(max = 255) String email,
        @NotNull Role role,
        Long promotionId,
        @Size(max = 50) String numEtudiant,
        @Size(max = 50) String matricule) {}
