package sn.ept.eval.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Modification d'un compte : le rôle ne change jamais. Les champs sans objet pour le rôle du compte sont ignorés. */
public record ModifierCompteRequest(
        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,
        @NotBlank @Size(max = 255) String email,
        Long promotionId,
        @Size(max = 50) String numEtudiant,
        @Size(max = 50) String matricule) {}
