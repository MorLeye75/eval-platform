package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import sn.ept.eval.dto.admin.ModifierCompteRequest;
import sn.ept.eval.exception.OperationCompteRefuseeException;
import sn.ept.eval.model.Administrateur;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.repository.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Règles de DS-16 qu'une requête HTTP ne peut pas atteindre seule : dernier administrateur, promotion d'un étudiant qui a composé. */
class AdminComptesServiceTest {

    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final EtudiantRepository etudiants = mock(EtudiantRepository.class);
    private final EnseignantRepository enseignants = mock(EnseignantRepository.class);
    private final AdministrateurRepository administrateurs = mock(AdministrateurRepository.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final SoumissionRepository soumissions = mock(SoumissionRepository.class);
    private final sn.ept.eval.repository.EvaluationRepository evaluations = mock(sn.ept.eval.repository.EvaluationRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private AdminComptesService service;

    @BeforeEach
    void setUp() {
        service = new AdminComptesService(utilisateurs, etudiants, enseignants, administrateurs, promotions, soumissions,
                evaluations, java.time.Clock.systemDefaultZone(), encoder, new GenerateurMotDePasse());
        when(utilisateurs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Administrateur admin(long id, String email) {
        Administrateur a = new Administrateur("Nom", "Prenom", email, "x");
        a.setId(id);
        return a;
    }

    @Test
    @DisplayName("Supprimer une promotion liée à une évaluation : refusé, rien n'est supprimé ; vide : supprimée")
    void supprimerPromotionLieeAUneEvaluation() {
        sn.ept.eval.model.Promotion promotion = new sn.ept.eval.model.Promotion("DIC1", "2026");
        promotion.setId(7L);
        when(promotions.findById(7L)).thenReturn(Optional.of(promotion));
        when(etudiants.countByPromotionId(7L)).thenReturn(0L);
        when(evaluations.countByPromotionsId(7L)).thenReturn(2L);

        assertThatThrownBy(() -> service.supprimerPromotion(7L))
                .isInstanceOf(OperationCompteRefuseeException.class).hasMessageContaining("2 évaluation(s)");
        verify(promotions, never()).delete(any());

        when(evaluations.countByPromotionsId(7L)).thenReturn(0L);
        service.supprimerPromotion(7L);
        verify(promotions).delete(promotion);
    }

    @Test
    @DisplayName("Désactiver le dernier administrateur actif (par un autre compte) : refusé, rien n'est écrit")
    void dernierAdministrateurActif() {
        Administrateur cible = admin(2, "cible@ept.sn");
        when(utilisateurs.findById(2L)).thenReturn(Optional.of(cible));
        when(administrateurs.findActifsForUpdate()).thenReturn(List.of(cible));

        assertThatThrownBy(() -> service.changerEtat(2L, false, "autre@ept.sn"))
                .isInstanceOf(OperationCompteRefuseeException.class).hasMessageContaining("au moins un administrateur actif");
        assertThat(cible.getActif()).isTrue();
        verify(utilisateurs, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Désactiver un administrateur quand un autre reste actif : accepté")
    void unAutreAdministrateurReste() {
        Administrateur cible = admin(2, "cible@ept.sn");
        when(utilisateurs.findById(2L)).thenReturn(Optional.of(cible));
        when(administrateurs.findActifsForUpdate()).thenReturn(List.of(cible, admin(3, "autre@ept.sn")));

        assertThat(service.changerEtat(2L, false, "autre@ept.sn").actif()).isFalse();
    }

    @Test
    @DisplayName("Désactiver son propre compte : refusé, sans tenir compte de la casse de l'e-mail du jeton")
    void sonPropreCompte() {
        Administrateur moi = admin(1, "moi@ept.sn");
        when(utilisateurs.findById(1L)).thenReturn(Optional.of(moi));

        assertThatThrownBy(() -> service.changerEtat(1L, false, "Moi@EPT.sn"))
                .isInstanceOf(OperationCompteRefuseeException.class).hasMessageContaining("propre compte");
        verify(administrateurs, never()).findActifsForUpdate();
    }

    @Test
    @DisplayName("Réactiver n'est jamais bloqué par les garde-fous")
    void reactiver() {
        Administrateur inactif = admin(2, "cible@ept.sn");
        inactif.setActif(false);
        when(utilisateurs.findById(2L)).thenReturn(Optional.of(inactif));

        assertThat(service.changerEtat(2L, true, "moi@ept.sn").actif()).isTrue();
        verify(administrateurs, never()).findActifsForUpdate();
    }

    @Test
    @DisplayName("Un étudiant qui a des copies ne change plus de promotion ; sans copie, il le peut ; même promotion : toujours permis")
    void promotionDUnEtudiantQuiACompose() {
        Promotion dic2 = promotion(1, "DIC2"), dic3 = promotion(2, "DIC3");
        Etudiant etudiant = new Etudiant("Sy", "Ali", "ali@ept.sn", "x", "N-1", dic2);
        etudiant.setId(10L);
        when(utilisateurs.findById(10L)).thenReturn(Optional.of(etudiant));
        when(promotions.findById(2L)).thenReturn(Optional.of(dic3));
        when(promotions.findById(1L)).thenReturn(Optional.of(dic2));
        when(etudiants.findByNumEtudiant(any())).thenReturn(Optional.empty());
        when(utilisateurs.findByEmail(any())).thenReturn(Optional.empty());

        when(soumissions.existsByEtudiantId(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.modifier(10L, requete(2L)))
                .isInstanceOf(OperationCompteRefuseeException.class).hasMessageContaining("déjà des copies");
        assertThat(etudiant.getPromotion()).isSameAs(dic2);
        assertThat(service.modifier(10L, requete(1L)).promotionId()).isEqualTo(1L);

        when(soumissions.existsByEtudiantId(10L)).thenReturn(false);
        assertThat(service.modifier(10L, requete(2L)).promotionId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Les mots de passe provisoires : 12 caractères, sans caractère ambigu, différents d'un appel à l'autre")
    void motsDePasseProvisoires() {
        GenerateurMotDePasse generateur = new GenerateurMotDePasse();
        String premier = generateur.generer();
        assertThat(premier).hasSize(12).matches("[A-HJ-NP-Za-km-z2-9]+");
        assertThat(generateur.generer()).isNotEqualTo(premier);
    }

    private static Promotion promotion(long id, String nom) {
        Promotion p = new Promotion(nom, "2026");
        p.setId(id);
        return p;
    }

    private static ModifierCompteRequest requete(Long promotionId) {
        return new ModifierCompteRequest("Sy", "Ali", "ali@ept.sn", promotionId, "N-1", null);
    }
}
