package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.dto.soumission.SauvegardeResponse;
import sn.ept.eval.dto.soumission.SoumissionDetailResponse;
import sn.ept.eval.dto.soumission.SoumissionResponse;
import sn.ept.eval.service.SoumissionService;

import java.util.Map;

@RestController
@RequestMapping("/soumissions")
@Tag(name = "Soumissions", description = "Endpoints de passation d'examen : sauvegarde périodique (DS-02), soumission finale (DS-01) et anti-triche")
public class SoumissionController {

    private final SoumissionService soumissionService;

    public SoumissionController(SoumissionService soumissionService) {
        this.soumissionService = soumissionService;
    }

    /**
     * Démarrer ou reprendre une session d'évaluation (étudiant).
     */
    @Operation(
            summary = "Démarrer ou reprendre une session d'évaluation",
            description = "Démarre l'épreuve pour l'étudiant connecté ou restaure sa session en cours avec le calcul du temps restant et la liste des questions."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Session initialisée ou reprise avec succès",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SoumissionDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Évaluation non ouverte, clôturée ou en brouillon",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Accès refusé : l'étudiant n'appartient pas à la promotion ciblée",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Évaluation introuvable",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/evaluations/{evaluationId}/demarrer")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<SoumissionDetailResponse> demarrerOuReprendre(
            @PathVariable Long evaluationId,
            Authentication authentication) {
        SoumissionDetailResponse response = soumissionService.demarrerOuReprendre(evaluationId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Sauvegarde automatique des réponses toutes les 30s (Diagramme de Séquence 2).
     * Endpoint : PUT /api/soumissions/{id}/sauvegarder
     */
    @Operation(
            summary = "Sauvegarde périodique 30s des réponses (Diagramme de Séquence DS-02)",
            description = "Sauvegarde incrémentale des réponses de l'étudiant. Vérifie que la copie est EN_COURS et que le temps n'est pas expiré (tolérance 2 min)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Sauvegarde enregistrée avec succès",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SauvegardeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erreur de validation des réponses transmises",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflit avec l'état de la copie : temps d'examen écoulé ou copie déjà verrouillée (norme RFC 7807)",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(
                                    name = "TempsExpireSauvegarde",
                                    summary = "Temps d'examen dépassé",
                                    value = "{\n  \"type\": \"https://eval.ept.sn/errors/temps-expire\",\n  \"title\": \"Temps d'examen dépassé\",\n  \"status\": 409,\n  \"detail\": \"Action rejetée : temps imparti expiré.\",\n  \"instance\": \"/api/soumissions/100/sauvegarder\",\n  \"code\": \"TEMPS_EXPIRE\",\n  \"timestamp\": \"2026-10-18T12:00:00\"\n}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Soumission introuvable",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PutMapping("/{id}/sauvegarder")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<SauvegardeResponse> sauvegarder(
            @PathVariable Long id,
            @Valid @RequestBody SauvegardeReponsesRequest request,
            Authentication authentication) {
        SauvegardeResponse response = soumissionService.sauvegarderReponses(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Soumission finale de la copie (Diagramme de Séquence 1).
     * Endpoint : POST /api/soumissions/{id}/soumettre
     */
    @Operation(
            summary = "Soumission finale de la copie (Diagramme de Séquence DS-01)",
            description = "Verrouille la copie au statut SOUMIS et déclenche la correction asynchrone (@Async). En cas de dépassement au-delà de la tolérance (2 min), la copie est figée au dernier auto-save et une alerte anti-triche est levée."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Copie soumise avec succès",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SoumissionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Dépassement temporel critique : copie figée à son dernier auto-save et alerte anti-triche (norme RFC 7807)",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(
                                    name = "TempsExpireCritique",
                                    summary = "Dépassement temporel critique",
                                    value = "{\n  \"type\": \"https://eval.ept.sn/errors/temps-expire\",\n  \"title\": \"Temps d'examen dépassé\",\n  \"status\": 409,\n  \"detail\": \"Temps dépassé au-delà de la marge de tolérance. Votre copie a été verrouillée à l'état de votre dernière sauvegarde.\",\n  \"instance\": \"/api/soumissions/100/soumettre\",\n  \"code\": \"TEMPS_EXPIRE\",\n  \"timestamp\": \"2026-10-18T12:00:00\"\n}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Soumission introuvable",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/{id}/soumettre")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<SoumissionResponse> soumettre(
            @PathVariable Long id,
            Authentication authentication) {
        SoumissionResponse response = soumissionService.soumettreCopie(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Détection anti-triche : signalement d'un changement d'onglet ou sortie plein écran.
     * Endpoint : POST /api/soumissions/{id}/changement-onglet
     */
    @Operation(
            summary = "Détection anti-triche : signalement de changement d'onglet ou sortie plein écran",
            description = "Enregistre un événement de navigation suspecte. Lève une AlerteAntiTriche dès 3 occurrences."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Événement anti-triche consigné avec succès"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Soumission introuvable",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/{id}/changement-onglet")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<Map<String, String>> signalerChangementOnglet(
            @PathVariable Long id,
            Authentication authentication) {
        soumissionService.enregistrerChangementOnglet(id, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Événement enregistré."));
    }

    /**
     * Obtenir le détail et l'état actuel de l'épreuve.
     * Endpoint : GET /api/soumissions/{id}
     */
    @Operation(
            summary = "Consulter l'état et le détail d'une copie d'examen",
            description = "Renvoie l'épreuve avec le décompte du temps restant, les questions et les réponses déjà saisies."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Détail de la soumission récupéré avec succès",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SoumissionDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Accès refusé : tentative de consultation de la copie d'un autre étudiant (protection IDOR)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Soumission introuvable",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ETUDIANT') or hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    public ResponseEntity<SoumissionDetailResponse> getDetail(
            @PathVariable Long id,
            Authentication authentication) {
        SoumissionDetailResponse response = soumissionService.getDetail(id, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
