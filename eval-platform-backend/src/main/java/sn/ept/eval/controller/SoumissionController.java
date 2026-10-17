package sn.ept.eval.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
public class SoumissionController {

    @Autowired
    private SoumissionService soumissionService;

    /**
     * Démarrer ou reprendre une session d'évaluation (étudiant).
     */
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
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ETUDIANT') or hasRole('ENSEIGNANT') or hasRole('ADMIN')")
    public ResponseEntity<SoumissionDetailResponse> getDetail(
            @PathVariable Long id,
            Authentication authentication) {
        SoumissionDetailResponse response = soumissionService.getDetail(id, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
