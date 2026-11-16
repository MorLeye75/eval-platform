package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Statistiques;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Supervision;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.TableauDeBord;
import sn.ept.eval.service.AdminVueEnsembleService;

/** Tableau de bord, supervision globale et statistiques globales de l'administrateur, en lecture seule (DS-18). */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Vue d'ensemble (admin)", description = "Tableau de bord, supervision globale et statistiques globales (DS-18)")
public class AdminVueEnsembleController {

    private final AdminVueEnsembleService service;

    public AdminVueEnsembleController(AdminVueEnsembleService service) {
        this.service = service;
    }

    @Operation(summary = "Tableau de bord", description = "Comptes actifs par rôle, évaluations, activité sur 7 jours, services connus.")
    @GetMapping("/tableau-de-bord")
    public ResponseEntity<TableauDeBord> tableauDeBord() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.tableauDeBord());
    }

    @Operation(summary = "Supervision globale", description = "Évaluations en cours de toute la plateforme et 20 dernières alertes. À relire toutes les 30 s.")
    @GetMapping("/supervision")
    public ResponseEntity<Supervision> supervision() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.supervision());
    }

    @Operation(summary = "Statistiques globales", description = "Notes sur 20 des copies notées, taux de remise, détail par promotion ; indicateur null sans copie notée.")
    @GetMapping("/statistiques")
    public ResponseEntity<Statistiques> statistiques() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.statistiques());
    }
}
