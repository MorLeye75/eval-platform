package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.evaluation.PromotionResponse;
import sn.ept.eval.service.PromotionService;

import java.util.List;

/** Promotions de la plateforme, pour associer une évaluation à ses destinataires (DS-03). */
@RestController
@RequestMapping("/promotions")
@Tag(name = "Promotions", description = "Liste des promotions, pour l'assistant de création d'évaluation (DS-03)")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Operation(summary = "Liste des promotions",
            description = "Triées par année décroissante puis par nom, avec leur effectif. Réservé aux enseignants et aux administrateurs : "
                    + "un étudiant n'a pas à connaître les autres promotions.")
    @GetMapping
    @PreAuthorize("hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    public ResponseEntity<List<PromotionResponse>> lister() {
        return ResponseEntity.ok(promotionService.lister());
    }
}
