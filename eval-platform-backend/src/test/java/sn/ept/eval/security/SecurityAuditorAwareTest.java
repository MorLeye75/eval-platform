package sn.ept.eval.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import sn.ept.eval.security.services.SecurityAuditorAware;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditorAwareTest {

    private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Audit Trail : Sans contexte de sécurité, l'auditeur par défaut est 'SYSTEM'")
    void getCurrentAuditor_SansContexte_RenvoieSystem() {
        SecurityContextHolder.clearContext();

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).isPresent().contains("SYSTEM");
    }

    @Test
    @DisplayName("Audit Trail : Avec token anonyme, l'auditeur est 'SYSTEM'")
    void getCurrentAuditor_Anonyme_RenvoieSystem() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).isPresent().contains("SYSTEM");
    }

    @Test
    @DisplayName("Audit Trail : Avec utilisateur JWT authentifié, l'auditeur est son email")
    void getCurrentAuditor_UtilisateurConnecte_RenvoieSonEmail() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "prof@ept.sn", "password", List.of(new SimpleGrantedAuthority("ROLE_ENSEIGNANT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).isPresent().contains("prof@ept.sn");
    }
}
