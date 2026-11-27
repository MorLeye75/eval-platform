package sn.ept.eval.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import sn.ept.eval.model.Administrateur;
import sn.ept.eval.repository.AdministrateurRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Configuration du premier administrateur : refus clair si elle est incomplète ou trop faible. */
class AdministrateurInitialInitializerTest {

    private final AdministrateurRepository administrateurs = mock(AdministrateurRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);

    private AdministrateurInitialInitializer initializer(String email, String motDePasse) {
        return new AdministrateurInitialInitializer(administrateurs, utilisateurs, new BCryptPasswordEncoder(4), email, motDePasse);
    }

    @Test
    @DisplayName("Aucun administrateur et variables absentes : le démarrage continue, rien n'est créé (avertissement dans le journal)")
    void sansVariables() {
        when(administrateurs.count()).thenReturn(0L);
        initializer("", "").run();
        verify(utilisateurs, never()).save(any());
    }

    @Test
    @DisplayName("Une seule des deux variables : démarrage refusé, avec la raison")
    void configurationIncomplete() {
        when(administrateurs.count()).thenReturn(0L);
        assertThatThrownBy(() -> initializer("direction@ept.sn", "").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_INITIAL_MOT_DE_PASSE");
        assertThatThrownBy(() -> initializer("", "MotDePasseInitial2026").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_INITIAL_EMAIL");
        verify(utilisateurs, never()).save(any());
    }

    @Test
    @DisplayName("Mot de passe de moins de 12 caractères, ou adresse invalide : démarrage refusé")
    void configurationTropFaible() {
        when(administrateurs.count()).thenReturn(0L);
        assertThatThrownBy(() -> initializer("direction@ept.sn", "court").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("12");
        assertThatThrownBy(() -> initializer("pas-une-adresse", "MotDePasseInitial2026").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_INITIAL_EMAIL");
        verify(utilisateurs, never()).save(any());
    }

    @Test
    @DisplayName("L'adresse appartient déjà à un compte (non administrateur) : démarrage refusé plutôt que de le doubler")
    void adresseDejaPrise() {
        when(administrateurs.count()).thenReturn(0L);
        when(utilisateurs.existsByEmail("direction@ept.sn")).thenReturn(true);
        assertThatThrownBy(() -> initializer("direction@ept.sn", "MotDePasseInitial2026").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("direction@ept.sn");
        verify(utilisateurs, never()).save(any(Administrateur.class));
    }
}
