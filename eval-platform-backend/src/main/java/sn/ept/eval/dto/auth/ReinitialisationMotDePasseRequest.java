package sn.ept.eval.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nouveau mot de passe choisi depuis le lien reçu par e-mail. La limite de 72 octets (BCrypt) est vérifiée par le service. */
public record ReinitialisationMotDePasseRequest(
        @NotBlank(message = "Le lien de réinitialisation est incomplet") String token,
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le nouveau mot de passe doit contenir entre 8 et 72 caractères")
        String motDePasse) {
}
