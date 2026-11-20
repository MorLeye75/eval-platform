package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.evaluation.ComparaisonEvaluationsResponse;
import sn.ept.eval.service.ComparaisonEvaluationsService;

import java.util.List;

/** Comparaison de plusieurs évaluations par leur enseignant (DS-19). */
@RestController
@RequestMapping("/enseignant/comparaison")
@PreAuthorize("hasRole('ENSEIGNANT')")
@Tag(name = "Comparaison d'évaluations (enseignant)", description = "Indicateurs sur 20 de 2 à 6 évaluations côte à côte (DS-19)")
public class ComparaisonController {

    private final ComparaisonEvaluationsService comparaisonService;

    public ComparaisonController(ComparaisonEvaluationsService comparaisonService) {
        this.comparaisonService = comparaisonService;
    }

    @Operation(summary = "Comparer des évaluations",
            description = "ids = 2 à 6 identifiants distincts, dans l'ordre d'affichage. Mêmes indicateurs que les statistiques (DS-09) ; "
                    + "403, 404 ou 409 si l'une des évaluations n'est pas accessible ou pas encore ouverte.")
    @GetMapping
    public ResponseEntity<ComparaisonEvaluationsResponse> comparer(@RequestParam List<Long> ids, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comparaisonService.comparer(ids, authentication.getName()));
    }
}
