package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.security.jwt.JwtUtils;
import sn.ept.eval.security.services.RevocationJetonsService;

/**
 * Déconnexion (DS-14) : révoque le jeton de la session fermée. La requête a passé le filtre d'authentification,
 * son jeton est donc valide et pas encore révoqué.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentification")
public class DeconnexionController {

    private static final String PREFIXE = "Bearer ";

    private final JwtUtils jwtUtils;
    private final RevocationJetonsService revocationJetonsService;

    public DeconnexionController(JwtUtils jwtUtils, RevocationJetonsService revocationJetonsService) {
        this.jwtUtils = jwtUtils;
        this.revocationJetonsService = revocationJetonsService;
    }

    @Operation(summary = "Se déconnecter",
            description = "Révoque le jeton de cette session jusqu'à son expiration. Les sessions ouvertes sur d'autres "
                    + "appareils restent valides.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session fermée, jeton révoqué"),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide, expiré ou déjà révoqué")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> deconnexion(HttpServletRequest request) {
        String entete = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (entete != null && entete.startsWith(PREFIXE)) {
            JwtUtils.JetonLu jeton = jwtUtils.lireJeton(entete.substring(PREFIXE.length()));
            revocationJetonsService.revoquer(jeton.jti(), jeton.expiration());
        }
        return ResponseEntity.noContent().build();
    }
}
