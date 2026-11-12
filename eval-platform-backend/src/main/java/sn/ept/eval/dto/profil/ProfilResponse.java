package sn.ept.eval.dto.profil;

import com.fasterxml.jackson.annotation.JsonInclude;
import sn.ept.eval.model.enums.Role;

/**
 * Profil de l'utilisateur connecté, en lecture seule : l'identité est gérée par l'administrateur.
 * numEtudiant et promotion ne sont renseignés que pour un étudiant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfilResponse(Long id, String nom, String prenom, String email, Role role,
                             String numEtudiant, String promotion) {
}
