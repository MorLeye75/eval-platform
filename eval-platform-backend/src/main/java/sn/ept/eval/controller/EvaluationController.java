package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.evaluation.AmenagementRequestDTO;
import sn.ept.eval.dto.evaluation.AmenagementResponse;
import sn.ept.eval.dto.evaluation.CreateEvaluationRequest;
import sn.ept.eval.dto.evaluation.EvaluationResponse;
import sn.ept.eval.dto.evaluation.ResultatsPublicationResponse;
import sn.ept.eval.service.AmenagementTempsService;
import sn.ept.eval.service.EvaluationService;
import sn.ept.eval.service.PublicationResultatsService;

import java.net.URI;
import java.util.List;

/** Création et cycle de vie des évaluations côté enseignant (DS-03, DS-04, DS-06). */
@RestController
@RequestMapping("/enseignant/evaluations")
@PreAuthorize("hasRole('ENSEIGNANT')")
@Tag(name = "Évaluations (enseignant)",
        description = "Création, modification, publication, aménagement de temps et publication des résultats (DS-03 à DS-07)")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final AmenagementTempsService amenagementTempsService;
    private final PublicationResultatsService publicationResultatsService;

    public EvaluationController(EvaluationService evaluationService,
                                AmenagementTempsService amenagementTempsService,
                                PublicationResultatsService publicationResultatsService) {
        this.evaluationService = evaluationService;
        this.amenagementTempsService = amenagementTempsService;
        this.publicationResultatsService = publicationResultatsService;
    }

    @Operation(summary = "Créer un brouillon",
            description = "Crée une évaluation en BROUILLON avec ses questions. 400 si une règle de question est violée, 404 si une promotion est inconnue.")
    @PostMapping
    public ResponseEntity<EvaluationResponse> creer(@Valid @RequestBody CreateEvaluationRequest request,
                                                    Authentication authentication) {
        EvaluationResponse created = evaluationService.creer(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/enseignant/evaluations/" + created.getId())).body(created);
    }

    @Operation(summary = "Mes évaluations", description = "Liste sans les questions, les plus récentes d'abord.")
    @GetMapping
    public ResponseEntity<List<EvaluationResponse>> mesEvaluations(Authentication authentication) {
        return ResponseEntity.ok(evaluationService.mesEvaluations(authentication.getName()));
    }

    @Operation(summary = "Détail d'une évaluation", description = "Avec les questions et les bonnes réponses. 403 si l'évaluation appartient à un autre enseignant.")
    @GetMapping("/{id}")
    public ResponseEntity<EvaluationResponse> details(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(evaluationService.details(id, authentication.getName()));
    }

    @Operation(summary = "Modifier un brouillon", description = "409 (EVALUATION_NON_MODIFIABLE) si l'évaluation n'est plus un brouillon, 409 (CONFLIT_CONCURRENT) en cas de modification simultanée.")
    @PutMapping("/{id}")
    public ResponseEntity<EvaluationResponse> modifier(@PathVariable Long id,
                                                       @Valid @RequestBody CreateEvaluationRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(evaluationService.modifier(id, request, authentication.getName()));
    }

    @Operation(summary = "Supprimer un brouillon")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id, Authentication authentication) {
        evaluationService.supprimer(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publier", description = "BROUILLON → PUBLIEE. 400 sans question ou si la fermeture est déjà passée.")
    @PostMapping("/{id}/publier")
    public ResponseEntity<EvaluationResponse> publier(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(evaluationService.publier(id, authentication.getName()));
    }

    @Operation(summary = "Repasser en brouillon", description = "PUBLIEE → BROUILLON, uniquement si aucune copie n'existe.")
    @PostMapping("/{id}/depublier")
    public ResponseEntity<EvaluationResponse> depublier(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(evaluationService.depublier(id, authentication.getName()));
    }

    @Operation(summary = "Publier les résultats",
            description = "Rend les notes et les corrigés consultables par les étudiants (DS-07) et notifie une fois chaque copie déjà notée. "
                    + "409 tant que l'évaluation n'est pas terminée (marge de tolérance comprise) ou si les résultats sont déjà publiés. "
                    + "Les copies pas encore notées ne bloquent pas : leur étudiant est notifié quand elles le sont.")
    @PostMapping("/{id}/resultats/publier")
    public ResponseEntity<ResultatsPublicationResponse> publierResultats(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(publicationResultatsService.publier(id, authentication.getName()));
    }

    @Operation(summary = "Définir un aménagement de temps",
            description = "Durée personnalisée d'un étudiant, au moins égale à la durée standard. 409 dès que l'étudiant a commencé sa copie.")
    @PutMapping("/{id}/amenagements/{etudiantId}")
    public ResponseEntity<AmenagementResponse> definirAmenagement(@PathVariable Long id,
                                                                  @PathVariable Long etudiantId,
                                                                  @Valid @RequestBody AmenagementRequestDTO request,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(amenagementTempsService.definir(id, etudiantId, request.getDureeMinutes(), authentication.getName()));
    }

    @Operation(summary = "Supprimer un aménagement de temps")
    @DeleteMapping("/{id}/amenagements/{etudiantId}")
    public ResponseEntity<Void> supprimerAmenagement(@PathVariable Long id,
                                                     @PathVariable Long etudiantId,
                                                     Authentication authentication) {
        amenagementTempsService.supprimer(id, etudiantId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
