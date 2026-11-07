package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.security.jwt.JwtUtils;
import sn.ept.eval.security.services.UserDetailsImpl;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static String base64(int octets) {
        return Base64.getEncoder().encodeToString(new byte[octets]);
    }

    @Test
    @DisplayName("Un secret de moins de 256 bits empêche le démarrage")
    void secretTropCourt_Refuse() {
        assertThatThrownBy(() -> new JwtUtils(base64(31), 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits")
                .hasMessageContaining("248 fournis");
    }

    @Test
    @DisplayName("Un secret qui n'est pas en Base64 empêche le démarrage")
    void secretNonBase64_Refuse() {
        assertThatThrownBy(() -> new JwtUtils("pas du base64 !", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("Un secret de 256 bits signe et valide un jeton ; un autre secret le rejette")
    void secretValide_JetonSigneEtVerifie() {
        JwtUtils jwtUtils = new JwtUtils(Base64.getEncoder().encodeToString("cle-de-test-de-32-octets-minimum".getBytes()), 60_000);
        Etudiant etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-1", null);
        etudiant.setId(1L);
        UserDetailsImpl principal = UserDetailsImpl.build(etudiant);

        String jeton = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThat(jwtUtils.validateJwtToken(jeton)).isTrue();
        assertThat(jwtUtils.getUserNameFromJwtToken(jeton)).isEqualTo("etudiant@ept.sn");
        assertThat(new JwtUtils(base64(32), 60_000).validateJwtToken(jeton)).isFalse();
    }
}
