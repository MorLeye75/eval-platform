package sn.ept.eval.dto.profil;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changement de son propre mot de passe. La limite de 72 octets (BCrypt) est vérifiée par le service : un caractère
 * accentué compte pour deux octets.
 */
public record ChangementMotDePasseRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire") String motDePasseActuel,
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le nouveau mot de passe doit contenir entre 8 et 72 caractères")
        String nouveauMotDePasse) {
}
