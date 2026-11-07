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
import java.util.Date;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    /** HS256 exige une clé d'au moins 256 bits. */
    static final int TAILLE_MIN_CLE_OCTETS = 32;

    private final SecretKey cle;
    private final int jwtExpirationMs;

    /**
     * La clé est décodée et contrôlée au démarrage : un secret absent, mal encodé ou trop court
     * empêche l'application de démarrer, au lieu d'échouer à la première connexion.
     */
    public JwtUtils(@Value("${jwt.secret}") String jwtSecret,
                    @Value("${jwt.expiration-ms}") int jwtExpirationMs) {
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
    }

    private SecretKey key() {
        return cle;
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("id", userPrincipal.getId())
                .claim("role", userPrincipal.getRole().name())
                .claim("nom", userPrincipal.getNom())
                .claim("prenom", userPrincipal.getPrenom())
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
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
