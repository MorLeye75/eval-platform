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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sn.ept.eval.dto.auth.LoginRequest;
import sn.ept.eval.dto.auth.LoginResponse;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.repository.UtilisateurRepository;
import sn.ept.eval.security.jwt.JwtUtils;
import sn.ept.eval.security.services.UserDetailsImpl;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentification", description = "Endpoints d'authentification JWT et profil utilisateur")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UtilisateurRepository utilisateurRepository;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager, UtilisateurRepository utilisateurRepository, JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.utilisateurRepository = utilisateurRepository;
        this.jwtUtils = jwtUtils;
    }

    @Operation(
            summary = "Authentification de l'utilisateur (Login)",
            description = "Valide les identifiants de l'utilisateur (Admin, Enseignant, Étudiant) et génère un jeton JWT Bearer d'une durée de validité de 24h."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentification réussie avec retour du token JWT et du profil",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erreur de validation des champs email ou mot de passe",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Identifiants invalides (email ou mot de passe incorrect)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getMotDePasse())
        );

        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Mettre à jour la date de dernière connexion
        utilisateurRepository.findById(userDetails.getId()).ifPresent(u -> {
            u.setDerniereConnexion(LocalDateTime.now());
            utilisateurRepository.save(u);
        });

        LoginResponse response = new LoginResponse(
                jwt,
                userDetails.getId(),
                userDetails.getNom(),
                userDetails.getPrenom(),
                userDetails.getUsername(),
                userDetails.getRole()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Consulter le profil de l'utilisateur connecté",
            description = "Retourne les informations du compte actuellement authentifié via l'en-tête Authorization: Bearer <token>."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Profil utilisateur récupéré avec succès",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Non authentifié ou jeton JWT manquant/expiré",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).build();
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        LoginResponse response = new LoginResponse(
                null,
                userDetails.getId(),
                userDetails.getNom(),
                userDetails.getPrenom(),
                userDetails.getUsername(),
                userDetails.getRole()
        );

        return ResponseEntity.ok(response);
    }
}
