package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.resultats.PerformancesResponse;
import sn.ept.eval.dto.resultats.ResultatsCopieResponse;
import sn.ept.eval.service.ResultatsService;

@RestController
@Tag(name = "Résultats (étudiant)", description = "Consultation des résultats et tableau de bord de l'étudiant (DS-13)")
public class ResultatsController {

    private final ResultatsService resultatsService;

    public ResultatsController(ResultatsService resultatsService) {
        this.resultatsService = resultatsService;
    }

    @Operation(
            summary = "Résultats d'une copie",
            description = "État de la copie (NON_REMISE, EN_ATTENTE, PUBLIES). Une fois les résultats publiés : note, "
                    + "moyenne de la promotion (5 copies notées au moins), détail par question. Bonnes réponses des QCM "
                    + "après la fermeture de l'évaluation ; cas de test masqués réduits à leur verdict."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Résultats (ou état sans note)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultatsCopieResponse.class))),
            @ApiResponse(responseCode = "403", description = "Copie d'un autre étudiant",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Copie introuvable",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/soumissions/{id}/resultats")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<ResultatsCopieResponse> resultatsCopie(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(resultatsService.resultatsCopie(id, authentication.getName()));
    }

    @Operation(
            summary = "Mon tableau de bord",
            description = "Évaluations notées et publiées de l'étudiant, notes sur 20, moyenne anonyme de la promotion "
                    + "et écart à cette moyenne."
    )
    @ApiResponse(responseCode = "200", description = "Tableau de bord (historique vide s'il n'y a encore aucun résultat)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PerformancesResponse.class)))
    @GetMapping("/etudiant/performances")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<PerformancesResponse> performances(Authentication authentication) {
        return ResponseEntity.ok(resultatsService.performances(authentication.getName()));
    }
}
