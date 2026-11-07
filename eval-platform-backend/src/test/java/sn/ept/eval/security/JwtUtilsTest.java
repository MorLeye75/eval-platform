package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.security.jwt.JwtUtils;
import sn.ept.eval.security.services.UserDetailsImpl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final Instant T0 = Instant.parse("2026-10-19T10:00:00Z");
    private static final Clock HORLOGE = Clock.fixed(T0, ZoneOffset.UTC);
    private static final String SECRET = Base64.getEncoder().encodeToString("cle-de-test-de-32-octets-minimum".getBytes());

    private static JwtUtils a(Duration decalage, long expirationMs) {
        return new JwtUtils(SECRET, expirationMs, Clock.fixed(T0.plus(decalage), ZoneOffset.UTC));
    }

    private static String jetonEmisA(Duration decalage, long expirationMs) {
        Etudiant etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-1", null);
        etudiant.setId(1L);
        UserDetailsImpl principal = UserDetailsImpl.build(etudiant);
        return a(decalage, expirationMs)
                .generateJwtToken(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static String base64(int octets) {
        return Base64.getEncoder().encodeToString(new byte[octets]);
    }

    @Test
    @DisplayName("Un secret de moins de 256 bits empêche le démarrage")
    void secretTropCourt_Refuse() {
        assertThatThrownBy(() -> new JwtUtils(base64(31), 60_000, HORLOGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits")
                .hasMessageContaining("248 fournis");
    }

    @Test
    @DisplayName("Un secret qui n'est pas en Base64 empêche le démarrage")
    void secretNonBase64_Refuse() {
        assertThatThrownBy(() -> new JwtUtils("pas du base64 !", 60_000, HORLOGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("Un secret de 256 bits signe et valide un jeton ; un autre secret le rejette")
    void secretValide_JetonSigneEtVerifie() {
        JwtUtils jwtUtils = new JwtUtils(Base64.getEncoder().encodeToString("cle-de-test-de-32-octets-minimum".getBytes()), 60_000, HORLOGE);
        Etudiant etudiant = new Etudiant("Niang", "Aminou", "etudiant@ept.sn", "pass", "ETU-1", null);
        etudiant.setId(1L);
        UserDetailsImpl principal = UserDetailsImpl.build(etudiant);

        String jeton = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThat(jwtUtils.validateJwtToken(jeton)).isTrue();
        assertThat(jwtUtils.getUserNameFromJwtToken(jeton)).isEqualTo("etudiant@ept.sn");
        assertThat(new JwtUtils(base64(32), 60_000, HORLOGE).validateJwtToken(jeton)).isFalse();
    }

    @Test
    @DisplayName("L'expiration est vérifiée sur l'horloge de l'application")
    void expiration_HorlogeDeLApplication() {
        String jeton = jetonEmisA(Duration.ZERO, Duration.ofHours(1).toMillis());

        assertThat(a(Duration.ofMinutes(59), 0).validateJwtToken(jeton)).isTrue();
        assertThat(a(Duration.ofMinutes(61), 0).validateJwtToken(jeton)).isFalse();
    }

    @Test
    @DisplayName("Une durée de validité au-delà de 24,8 jours (dépassement d'un int en ms) est respectée")
    void expirationLongue_SansDepassement() {
        long trenteJours = Duration.ofDays(30).toMillis(); // 2 592 000 000 > Integer.MAX_VALUE
        String jeton = jetonEmisA(Duration.ZERO, trenteJours);

        assertThat(a(Duration.ofDays(29), 0).validateJwtToken(jeton)).isTrue();
        assertThat(a(Duration.ofDays(31), 0).validateJwtToken(jeton)).isFalse();
    }
}
