package sn.ept.eval.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Demande d'un lien de réinitialisation du mot de passe. */
public record MotDePasseOublieRequest(
        @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "L'adresse e-mail n'est pas valide")
        String email) {
}
