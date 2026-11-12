package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.profil.ChangementMotDePasseRequest;
import sn.ept.eval.dto.profil.ProfilResponse;
import sn.ept.eval.service.ProfilService;

/** Profil de l'utilisateur connecté, quel que soit son rôle. */
@RestController
@RequestMapping("/profil")
@Tag(name = "Profil", description = "Consultation du profil et changement de son mot de passe")
public class ProfilController {

    private final ProfilService profilService;

    public ProfilController(ProfilService profilService) {
        this.profilService = profilService;
    }

    @Operation(summary = "Consulter son profil",
            description = "Identité en lecture seule (modifiable par l'administrateur) ; matricule et promotion pour un étudiant.")
    @GetMapping
    public ProfilResponse profil(Authentication authentication) {
        return profilService.profil(authentication.getName());
    }

    @Operation(summary = "Changer son mot de passe",
            description = "Exige le mot de passe actuel. Les échecs comptent avec ceux de la connexion (blocage 429).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mot de passe changé"),
            @ApiResponse(responseCode = "400", description = "Mot de passe actuel incorrect, ou nouveau mot de passe invalide",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Trop d'échecs récents (en-tête Retry-After)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/mot-de-passe")
    public ResponseEntity<Void> changerMotDePasse(@Valid @RequestBody ChangementMotDePasseRequest demande,
                                                  Authentication authentication, HttpServletRequest request) {
        // IP du client : derrière un reverse proxy, celle transmise par le proxy si FORWARD_HEADERS_STRATEGY=native
        profilService.changerMotDePasse(authentication.getName(), demande, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
