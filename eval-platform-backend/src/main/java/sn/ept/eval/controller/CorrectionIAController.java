package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.ept.eval.dto.correction.*;
import java.util.List;
import sn.ept.eval.model.enums.StatutProposition;
import sn.ept.eval.service.CorrectionIAService;
import sn.ept.eval.service.GenerationCorrectionIAService;

/** Correction assistée par IA et validation par l'enseignant (DS-08). */
@RestController
@RequestMapping("/enseignant")
@PreAuthorize("hasRole('ENSEIGNANT')")
@Tag(name = "Correction IA (enseignant)", description = "Propositions de l'IA pour les questions ouvertes, validation et notes manuelles (DS-08)")
public class CorrectionIAController {

    private final CorrectionIAService correctionService;
    private final GenerationCorrectionIAService generationService;

    public CorrectionIAController(CorrectionIAService correctionService, GenerationCorrectionIAService generationService) {
        this.correctionService = correctionService;
        this.generationService = generationService;
    }

    @Operation(summary = "Générer les propositions IA d'une évaluation terminée",
            description = "Asynchrone : répond 202 avec le nombre de réponses à proposer ; relire GET /enseignant/corrections-ia.")
    @PostMapping("/evaluations/{id}/corrections-ia/generer")
    public ResponseEntity<GenerationResponse> generer(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(generationService.generer(id, authentication.getName()));
    }

    @Operation(summary = "Propositions de l'IA", description = "Filtres facultatifs : evaluationId, statut. « enCours » = réponses sans proposition.")
    @GetMapping("/corrections-ia")
    public ResponseEntity<CorrectionsIAResponse> lister(@RequestParam(required = false) Long evaluationId,
                                                        @RequestParam(required = false) StatutProposition statut,
                                                        Authentication authentication) {
        return ResponseEntity.ok(correctionService.lister(authentication.getName(), evaluationId, statut));
    }

    @Operation(summary = "Réponses à noter à la main", description = "Réponses ouvertes sans proposition IA, pas encore notées. Filtre facultatif : evaluationId.")
    @GetMapping("/corrections-manuelles")
    public ResponseEntity<List<ReponseANoterResponse>> aNoter(@RequestParam(required = false) Long evaluationId,
                                                              Authentication authentication) {
        return ResponseEntity.ok(correctionService.aNoterAlaMain(authentication.getName(), evaluationId));
    }

    @Operation(summary = "Valider une proposition", description = "Note facultative : sans elle, la note de l'IA est retenue.")
    @PutMapping("/corrections-ia/{id}/valider")
    public ResponseEntity<NoteResponse> valider(@PathVariable Long id, @Valid @RequestBody(required = false) NoteRequest requete,
                                                Authentication authentication) {
        return ResponseEntity.ok(correctionService.valider(id, requete, authentication.getName()));
    }

    @Operation(summary = "Rejeter une proposition", description = "La note de l'enseignant est obligatoire.")
    @PutMapping("/corrections-ia/{id}/rejeter")
    public ResponseEntity<NoteResponse> rejeter(@PathVariable Long id, @Valid @RequestBody NoteRequest requete,
                                                Authentication authentication) {
        return ResponseEntity.ok(correctionService.rejeter(id, requete, authentication.getName()));
    }

    @Operation(summary = "Noter une réponse à la main", description = "Pour une réponse sans proposition ; une proposition en attente est rejetée.")
    @PutMapping("/reponses/{id}/note")
    public ResponseEntity<NoteResponse> noter(@PathVariable Long id, @Valid @RequestBody NoteRequest requete,
                                              Authentication authentication) {
        return ResponseEntity.ok(correctionService.noterManuellement(id, requete, authentication.getName()));
    }
}
