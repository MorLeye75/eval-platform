package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.ept.eval.dto.correction.CopieEnseignantResponse;
import sn.ept.eval.dto.correction.RevisionNoteRequest;
import sn.ept.eval.dto.correction.RevisionNoteResponse;
import sn.ept.eval.service.CopieEnseignantService;

/** Copie d'un étudiant vue par son enseignant, et révision d'une note avant la publication des résultats. */
@RestController
@RequestMapping("/enseignant")
@PreAuthorize("hasRole('ENSEIGNANT')")
@Tag(name = "Copies (enseignant)", description = "Relecture complète d'une copie et révision tracée d'une note avant publication")
public class CopieEnseignantController {

    private final CopieEnseignantService service;

    public CopieEnseignantController(CopieEnseignantService service) {
        this.service = service;
    }

    @Operation(summary = "Copie complète d'un étudiant",
            description = "Chaque question : réponse (texte, code, choix cochés), verdicts des cas de test, note et historique des révisions.")
    @GetMapping("/soumissions/{id}/copie")
    public ResponseEntity<CopieEnseignantResponse> copie(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(service.copie(id, authentication.getName()));
    }

    @Operation(summary = "Réviser une note avant publication",
            description = "Pour une réponse déjà notée (QCM, Judge0, IA ou à la main). Motif obligatoire, tracé et montré à "
                    + "l'étudiant. 409 CORRECTION_NON_MODIFIABLE si les résultats sont publiés ou la réponse pas encore notée.")
    @PutMapping("/reponses/{id}/revision")
    public ResponseEntity<RevisionNoteResponse> reviser(@PathVariable Long id, @Valid @RequestBody RevisionNoteRequest requete,
                                                        Authentication authentication) {
        return ResponseEntity.ok(service.reviser(id, requete, authentication.getName()));
    }
}
