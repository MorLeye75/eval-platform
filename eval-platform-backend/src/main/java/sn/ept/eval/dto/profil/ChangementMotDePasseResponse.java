package sn.ept.eval.dto.profil;

/**
 * Réponse au changement de son mot de passe : les jetons émis avant le changement sont refusés (autres appareils
 * déconnectés), la session en cours continue avec ce nouveau jeton.
 */
public record ChangementMotDePasseResponse(String token) {
}
