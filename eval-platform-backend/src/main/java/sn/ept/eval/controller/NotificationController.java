package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.ept.eval.dto.notification.NombreNonLuesResponse;
import sn.ept.eval.dto.notification.PageNotificationsResponse;
import sn.ept.eval.security.services.UserDetailsImpl;
import sn.ept.eval.service.MesNotificationsService;

/** Notifications de l'utilisateur connecté (DS-15), tous rôles. */
@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications", description = "Notifications internes : liste, compteur, marquage comme lues")
public class NotificationController {

    private final MesNotificationsService mesNotificationsService;

    public NotificationController(MesNotificationsService mesNotificationsService) {
        this.mesNotificationsService = mesNotificationsService;
    }

    /** Identifiant tiré du jeton : jamais de la requête. */
    private static Long moi(Authentication authentication) {
        return ((UserDetailsImpl) authentication.getPrincipal()).getId();
    }

    @Operation(summary = "Mes notifications", description = "Les plus récentes d'abord ; taille de 1 à 50 (20 par défaut).")
    @GetMapping
    public PageNotificationsResponse lister(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int taille,
                                            Authentication authentication) {
        return mesNotificationsService.page(moi(authentication), page, taille);
    }

    @Operation(summary = "Nombre de notifications non lues", description = "Compteur de la cloche, interrogé toutes les 60 s.")
    @GetMapping("/non-lues/nombre")
    public NombreNonLuesResponse nombreNonLues(Authentication authentication) {
        return mesNotificationsService.nombreNonLues(moi(authentication));
    }

    @Operation(summary = "Marquer une notification comme lue")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notification marquée comme lue"),
            @ApiResponse(responseCode = "404", description = "Inexistante, ou notification d'un autre utilisateur")
    })
    @PutMapping("/{id}/lire")
    public ResponseEntity<Void> marquerLue(@PathVariable Long id, Authentication authentication) {
        mesNotificationsService.marquerLue(id, moi(authentication));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Tout marquer comme lu")
    @PutMapping("/lire-tout")
    public ResponseEntity<Void> toutMarquerLu(Authentication authentication) {
        mesNotificationsService.toutMarquerLu(moi(authentication));
        return ResponseEntity.noContent().build();
    }
}
