package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.etudiant.EvaluationEtudiantResponse;
import sn.ept.eval.service.EvaluationEtudiantService;

import java.util.List;

@RestController
@RequestMapping("/etudiant/evaluations")
@Tag(name = "Évaluations (étudiant)", description = "Épreuves accessibles à l'étudiant connecté (DS-10)")
public class EvaluationEtudiantController {

    private final EvaluationEtudiantService evaluationEtudiantService;

    public EvaluationEtudiantController(EvaluationEtudiantService evaluationEtudiantService) {
        this.evaluationEtudiantService = evaluationEtudiantService;
    }

    @Operation(
            summary = "Mes évaluations",
            description = "Évaluations publiées, en cours ou terminées de la promotion de l'étudiant, avec l'état de sa copie et sa durée effective (tiers-temps compris)."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Liste des évaluations (vide si l'étudiant n'a pas de promotion)",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = EvaluationEtudiantResponse.class)))
    )
    @GetMapping
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<List<EvaluationEtudiantResponse>> mesEvaluations(Authentication authentication) {
        return ResponseEntity.ok(evaluationEtudiantService.mesEvaluations(authentication.getName()));
    }
}
