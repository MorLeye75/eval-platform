package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.evaluation.SupervisionResponse;
import sn.ept.eval.service.SupervisionService;

/** Supervision en lecture seule d'une évaluation ouverte, pour son enseignant (DS-17). */
@RestController
@RequestMapping("/enseignant/supervision")
@PreAuthorize("hasRole('ENSEIGNANT')")
@Tag(name = "Supervision (enseignant)", description = "Avancement et alertes anti-triche d'une évaluation en cours, relus par sondage (DS-17)")
public class SupervisionController {

    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @Operation(summary = "Avancement d'une évaluation",
            description = "Lecture seule : état de chaque étudiant attendu, réponses données, alertes. 409 avant l'ouverture. À relire toutes les 15 s.")
    @GetMapping("/{id}")
    public ResponseEntity<SupervisionResponse> supervision(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(supervisionService.supervision(id, authentication.getName()));
    }
}
