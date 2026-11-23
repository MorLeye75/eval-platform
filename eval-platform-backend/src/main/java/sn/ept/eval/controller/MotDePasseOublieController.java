package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.auth.MotDePasseOublieRequest;
import sn.ept.eval.dto.auth.ReinitialisationMotDePasseRequest;
import sn.ept.eval.service.ReinitialisationMotDePasseService;

/** Mot de passe oublié : routes publiques (l'utilisateur n'est pas connecté). */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentification")
public class MotDePasseOublieController {

    private final ReinitialisationMotDePasseService service;

    public MotDePasseOublieController(ReinitialisationMotDePasseService service) {
        this.service = service;
    }

    @Operation(summary = "Demander un lien de réinitialisation",
            description = "Envoie un lien valable 30 minutes à l'adresse du compte. Réponse identique que le compte "
                    + "existe ou non (on ne révèle pas qui a un compte) ; au plus 3 e-mails par compte en 15 minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Demande prise en compte"),
            @ApiResponse(responseCode = "400", description = "Adresse e-mail absente ou mal formée",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/mot-de-passe-oublie")
    public ResponseEntity<Void> demander(@Valid @RequestBody MotDePasseOublieRequest demande) {
        service.demander(demande.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Choisir un nouveau mot de passe depuis le lien reçu",
            description = "Lien à usage unique. Les sessions ouvertes du compte sont coupées ; un mot de passe provisoire est levé.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mot de passe réinitialisé"),
            @ApiResponse(responseCode = "400", description = "Lien invalide ou expiré (code JETON_INVALIDE), ou mot de passe invalide",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/reinitialiser-mot-de-passe")
    public ResponseEntity<Void> reinitialiser(@Valid @RequestBody ReinitialisationMotDePasseRequest demande) {
        service.reinitialiser(demande.token(), demande.motDePasse());
        return ResponseEntity.noContent().build();
    }
}
