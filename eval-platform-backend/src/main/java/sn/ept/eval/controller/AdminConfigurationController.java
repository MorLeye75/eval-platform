package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.admin.ConfigurationSystemeResponse;
import sn.ept.eval.service.ConfigurationSystemeService;

/** Configuration en vigueur, en lecture seule, pour l'administrateur (DS-18, section D). */
@RestController
@RequestMapping("/admin/configuration")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Configuration (administration)", description = "Ce que le serveur applique réellement, sans aucun secret (DS-18)")
public class AdminConfigurationController {

    private final ConfigurationSystemeService configurationService;

    public AdminConfigurationController(ConfigurationSystemeService configurationService) {
        this.configurationService = configurationService;
    }

    @Operation(summary = "Configuration en vigueur",
            description = "Lecture seule. Chaque paramètre indique sa source : CODE (fixé dans le code) ou DEPLOIEMENT (propriété ou variable d'environnement). Jamais de secret.")
    @GetMapping
    public ResponseEntity<ConfigurationSystemeResponse> configuration() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(configurationService.configuration());
    }
}
