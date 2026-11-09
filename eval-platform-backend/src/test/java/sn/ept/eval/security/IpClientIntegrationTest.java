package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.NestedTestConfiguration;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration.OVERRIDE;

/**
 * IP du client vue par la limite des tentatives de connexion, sur un vrai serveur Tomcat (l'en-tête
 * X-Forwarded-For n'est interprété que par Tomcat, pas par MockMvc). Chaque cas a sa base H2.
 */
class IpClientIntegrationTest {

    abstract static class Base {
        @Autowired
        TestRestTemplate http;

        @Autowired
        UtilisateurRepository utilisateurRepository;

        @Autowired
        PasswordEncoder passwordEncoder;

        String nouveauCompte() {
            String email = "ip-" + UUID.randomUUID() + "@ept.sn";
            utilisateurRepository.save(new Etudiant("Test", "Ip", email, passwordEncoder.encode("bon"), "ETU-" + UUID.randomUUID(), null));
            return email;
        }

        int connexion(String email, String motDePasse, String ipTransmise) {
            HttpHeaders entetes = new HttpHeaders();
            entetes.setContentType(MediaType.APPLICATION_JSON);
            entetes.set("X-Forwarded-For", ipTransmise);
            String corps = "{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}";
            return http.postForEntity("/auth/login", new HttpEntity<>(corps, entetes), String.class).getStatusCode().value();
        }
    }

    @Nested
    @NestedTestConfiguration(OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "spring.datasource.url=jdbc:h2:mem:ip-proxy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "server.forward-headers-strategy=native"
    })
    @DisplayName("Derrière un reverse proxy (FORWARD_HEADERS_STRATEGY=native)")
    class DerriereUnProxy extends Base {

        @Test
        @DisplayName("Chaque client est compté sous sa propre IP : bloquer l'un ne bloque pas les autres")
        void ipReelleDuClient() {
            String email = nouveauCompte();
            for (int i = 0; i < 5; i++) {
                assertThat(connexion(email, "mauvais", "203.0.113.10")).isEqualTo(401);
            }

            assertThat(connexion(email, "bon", "203.0.113.10")).isEqualTo(429);
            assertThat(connexion(email, "bon", "203.0.113.20")).isEqualTo(200);
        }
    }

    @Nested
    @NestedTestConfiguration(OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
            "spring.datasource.url=jdbc:h2:mem:ip-direct;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
    @DisplayName("Sans proxy (défaut : none)")
    class SansProxy extends Base {

        @Test
        @DisplayName("Un X-Forwarded-For inventé par le client ne permet pas de contourner le blocage")
        void enteteIgnore() {
            String email = nouveauCompte();
            for (int i = 0; i < 5; i++) {
                assertThat(connexion(email, "mauvais", "203.0.113." + i)).isEqualTo(401);
            }

            assertThat(connexion(email, "bon", "203.0.113.99")).isEqualTo(429);
        }
    }
}
