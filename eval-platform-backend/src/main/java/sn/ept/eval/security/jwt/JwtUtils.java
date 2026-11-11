package sn.ept.eval.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import sn.ept.eval.security.services.UserDetailsImpl;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    /** HS256 exige une clé d'au moins 256 bits. */
    static final int TAILLE_MIN_CLE_OCTETS = 32;

    private final SecretKey cle;
    private final long jwtExpirationMs;
    /** Horloge de l'application : émission et expiration des jetons (testables, cohérentes avec le reste). */
    private final Clock clock;
    private final JwtParser parser;

    /**
     * La clé est décodée et contrôlée au démarrage : un secret absent, mal encodé ou trop court
     * empêche l'application de démarrer, au lieu d'échouer à la première connexion.
     */
    public JwtUtils(@Value("${jwt.secret}") String jwtSecret,
                    @Value("${jwt.expiration-ms}") long jwtExpirationMs,
                    Clock clock) {
        byte[] octets;
        try {
            octets = Decoders.BASE64.decode(jwtSecret);
        } catch (RuntimeException e) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) doit être encodé en Base64.", e);
        }
        if (octets.length < TAILLE_MIN_CLE_OCTETS) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) doit faire au moins " + (TAILLE_MIN_CLE_OCTETS * 8)
                    + " bits une fois décodé (" + (octets.length * 8) + " fournis). Exemple : openssl rand -base64 64");
        }
        this.cle = Keys.hmacShaKeyFor(octets);
        this.jwtExpirationMs = jwtExpirationMs;
        this.clock = clock;
        this.parser = Jwts.parser().verifyWith(cle).clock(() -> Date.from(clock.instant())).build();
    }

    private SecretKey key() {
        return cle;
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        return Jwts.builder()
                // Identifiant unique : la déconnexion révoque ce jeton-là seulement (DS-14)
                .id(UUID.randomUUID().toString())
                .subject(userPrincipal.getUsername())
                .claim("id", userPrincipal.getId())
                .claim("role", userPrincipal.getRole().name())
                .claim("nom", userPrincipal.getNom())
                .claim("prenom", userPrincipal.getPrenom())
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusMillis(jwtExpirationMs)))
                .signWith(key())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return parser.parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /** Identifiant (jti) et expiration d'un jeton dont la signature et l'expiration sont valides. */
    public record JetonLu(String jti, LocalDateTime expiration) {}

    /**
     * Lit un jeton déjà validé ({@link #validateJwtToken}). jti nul pour un jeton émis avant l'ajout de la
     * révocation : il n'est pas révocable, mais expire comme les autres.
     */
    public JetonLu lireJeton(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return new JetonLu(claims.getId(), LocalDateTime.ofInstant(claims.getExpiration().toInstant(), clock.getZone()));
    }

    public boolean validateJwtToken(String authToken) {
        try {
            parser.parseSignedClaims(authToken);
            return true;
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT expiré: {}", e.getMessage());
        } catch (JwtException e) {
            // Mal formé, non supporté, signature invalide (SignatureException), etc.
            logger.error("Token JWT invalide: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("La chaîne de claims JWT est vide: {}", e.getMessage());
        }
        return false;
    }
}
