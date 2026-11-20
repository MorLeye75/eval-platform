package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.evaluation.ResultatsEvaluationResponse;
import sn.ept.eval.dto.evaluation.StatistiquesEvaluationResponse;
import sn.ept.eval.service.ExportResultatsService;
import sn.ept.eval.service.StatistiquesEvaluationService;

/** Statistiques et export des résultats d'une évaluation, pour son enseignant (DS-09). */
@RestController
@RequestMapping("/enseignant/evaluations/{id}")
@PreAuthorize("hasRole('ENSEIGNANT')")
@Tag(name = "Statistiques et export (enseignant)", description = "Statistiques sur 20 et export CSV des résultats (DS-09)")
public class StatistiquesController {

    private final StatistiquesEvaluationService statistiquesService;
    private final ExportResultatsService exportService;

    public StatistiquesController(StatistiquesEvaluationService statistiquesService, ExportResultatsService exportService) {
        this.statistiquesService = statistiquesService;
        this.exportService = exportService;
    }

    @Operation(summary = "Statistiques d'une évaluation",
            description = "Sur les copies notées, ramenées sur 20 ; indicateurs null tant qu'aucune copie n'est notée. 409 avant l'ouverture.")
    @GetMapping("/statistiques")
    public ResponseEntity<StatistiquesEvaluationResponse> statistiques(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(statistiquesService.statistiques(id, authentication.getName()));
    }

    @Operation(summary = "Notes par étudiant",
            description = "Une ligne par étudiant attendu (absents compris), mêmes données que l'export CSV, sans e-mail. 409 avant l'ouverture.")
    @GetMapping("/resultats")
    public ResponseEntity<ResultatsEvaluationResponse> resultats(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exportService.lignes(id, authentication.getName()));
    }

    @Operation(summary = "Export CSV du détail par question",
            description = "Une ligne par étudiant attendu et par question : ordre, type, points, note obtenue (vide tant que la copie n'est pas notée). Même format que l'export des résultats.")
    @GetMapping("/export/detail-csv")
    public ResponseEntity<byte[]> exporterDetail(@PathVariable Long id, Authentication authentication) {
        byte[] fichier = exportService.exporterDetail(id, authentication.getName());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("detail-" + id + ".csv").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(fichier);
    }

    @Operation(summary = "Export CSV des résultats",
            description = "Une ligne par étudiant attendu (absents compris), UTF-8 avec BOM, séparateur « ; », cellules protégées contre l'injection de formule.")
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exporter(@PathVariable Long id, Authentication authentication) {
        byte[] fichier = exportService.exporter(id, authentication.getName());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("resultats-" + id + ".csv").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(fichier);
    }
}
