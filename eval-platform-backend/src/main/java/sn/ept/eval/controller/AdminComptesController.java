package sn.ept.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sn.ept.eval.dto.admin.*;
import sn.ept.eval.dto.evaluation.PromotionResponse;
import sn.ept.eval.model.enums.Role;
import sn.ept.eval.service.AdminComptesService;
import sn.ept.eval.service.ImportEtudiantsService;

import java.util.List;

/** Administration des comptes, des promotions et de l'import d'étudiants (DS-16). */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Comptes, mots de passe provisoires, promotions et import CSV d'étudiants (DS-16)")
public class AdminComptesController {

    private final AdminComptesService comptesService;
    private final ImportEtudiantsService importService;

    public AdminComptesController(AdminComptesService comptesService, ImportEtudiantsService importService) {
        this.comptesService = comptesService;
        this.importService = importService;
    }

    @Operation(summary = "Liste des comptes", description = "Filtres facultatifs : role, actif, q (nom, prénom ou e-mail). Jamais de mot de passe.")
    @GetMapping("/utilisateurs")
    public ResponseEntity<List<UtilisateurAdminResponse>> lister(@RequestParam(required = false) Role role,
                                                                 @RequestParam(required = false) Boolean actif,
                                                                 @RequestParam(required = false) String q) {
        return ResponseEntity.ok(comptesService.lister(role, actif, q));
    }

    @Operation(summary = "Créer un compte", description = "Renvoie le mot de passe provisoire une seule fois.")
    @PostMapping("/utilisateurs")
    public ResponseEntity<CompteCreeResponse> creer(@Valid @RequestBody CreerCompteRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comptesService.creer(requete));
    }

    @Operation(summary = "Modifier un compte", description = "Le rôle ne change pas. Un étudiant qui a des copies ne change plus de promotion.")
    @PutMapping("/utilisateurs/{id}")
    public ResponseEntity<UtilisateurAdminResponse> modifier(@PathVariable Long id, @Valid @RequestBody ModifierCompteRequest requete) {
        return ResponseEntity.ok(comptesService.modifier(id, requete));
    }

    @Operation(summary = "Désactiver un compte", description = "Effet immédiat sur ses jetons. Refusé pour son propre compte et pour le dernier administrateur actif.")
    @PostMapping("/utilisateurs/{id}/desactiver")
    public ResponseEntity<UtilisateurAdminResponse> desactiver(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(comptesService.changerEtat(id, false, authentication.getName()));
    }

    @Operation(summary = "Réactiver un compte")
    @PostMapping("/utilisateurs/{id}/activer")
    public ResponseEntity<UtilisateurAdminResponse> activer(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(comptesService.changerEtat(id, true, authentication.getName()));
    }

    @Operation(summary = "Réinitialiser un mot de passe", description = "Renvoie un nouveau mot de passe provisoire, une seule fois.")
    @PostMapping("/utilisateurs/{id}/reinitialiser-mot-de-passe")
    public ResponseEntity<MotDePasseProvisoireResponse> reinitialiser(@PathVariable Long id) {
        return ResponseEntity.ok(comptesService.reinitialiserMotDePasse(id));
    }

    @Operation(summary = "Créer une promotion")
    @PostMapping("/promotions")
    public ResponseEntity<PromotionResponse> creerPromotion(@Valid @RequestBody CreerPromotionRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comptesService.creerPromotion(requete));
    }

    @Operation(summary = "Renommer une promotion", description = "Nom et année ; les étudiants et les évaluations y restent rattachés.")
    @PutMapping("/promotions/{id}")
    public ResponseEntity<PromotionResponse> modifierPromotion(@PathVariable Long id, @Valid @RequestBody CreerPromotionRequest requete) {
        return ResponseEntity.ok(comptesService.modifierPromotion(id, requete));
    }

    @Operation(summary = "Supprimer une promotion", description = "Refusé (409) si elle compte des étudiants ou des évaluations.")
    @DeleteMapping("/promotions/{id}")
    public ResponseEntity<Void> supprimerPromotion(@PathVariable Long id) {
        comptesService.supprimerPromotion(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Importer des étudiants (CSV)",
            description = "Colonnes : nom;prenom;email;numEtudiant;promotion. Tout ou rien : 200 si importé, 422 avec la liste des lignes en erreur sinon.")
    @PostMapping(value = "/etudiants/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportEtudiantsResponse> importer(@RequestParam("fichier") MultipartFile fichier) {
        ImportEtudiantsResponse reponse = importService.importer(fichier);
        return ResponseEntity.status(reponse.importe() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(reponse);
    }
}
