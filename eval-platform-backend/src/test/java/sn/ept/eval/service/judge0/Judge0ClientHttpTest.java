package sn.ept.eval.service.judge0;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import sn.ept.eval.config.Judge0Proprietes;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.service.judge0.Judge0Client.CasAExecuter;
import sn.ept.eval.service.judge0.Judge0Client.ResultatCas;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le client Judge0 tel que Spring le construit (vrai client HTTP du JDK), face à un serveur HTTP local.
 * Le vrai Judge0 (Rails) ignore un corps envoyé par morceaux (Transfer-Encoding: chunked) et répond 422
 * « source_code can't be blank » : chaque requête doit annoncer sa longueur (Content-Length).
 * MockRestServiceServer ne passe pas par le client HTTP et ne peut pas le voir.
 */
class Judge0ClientHttpTest {

    private HttpServer serveur;
    private final Map<String, String> entetesSoumission = new ConcurrentHashMap<>();
    private volatile String corpsSoumission = "";

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/submissions", echange -> {
            if ("POST".equals(echange.getRequestMethod())) {
                echange.getRequestHeaders().forEach((cle, valeurs) -> entetesSoumission.put(cle.toLowerCase(), valeurs.get(0)));
                corpsSoumission = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                repondre(echange, 201, "{\"token\":\"t1\"}");
            } else {
                repondre(echange, 200, "{\"stdout\":null,\"time\":\"0.01\",\"status\":{\"id\":3,\"description\":\"Accepted\"}}");
            }
        });
        serveur.start();
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    private static void repondre(com.sun.net.httpserver.HttpExchange echange, int code, String json) throws IOException {
        byte[] octets = json.getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders().add("Content-Type", "application/json");
        echange.sendResponseHeaders(code, octets.length);
        echange.getResponseBody().write(octets);
        echange.close();
    }

    @Test
    @DisplayName("Soumission envoyée avec sa longueur (Content-Length), jamais par morceaux : sinon Judge0 répond 422")
    void soumission_AvecContentLength() throws Exception {
        String url = "http://127.0.0.1:" + serveur.getAddress().getPort();
        Judge0Proprietes proprietes = new Judge0Proprietes(url, "jeton", 2, 2, 5, 256000, 60, 1024,
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(10), null);
        // Constructeur utilisé par Spring : vrai client HTTP du JDK, avec ses délais
        Judge0Client client = new Judge0Client(RestClient.builder(), proprietes, Clock.systemUTC());

        List<ResultatCas> resultats = client.executer("print(1)", "python",
                List.of(new CasAExecuter(1L, "", "1")), Instant.now().plusSeconds(30));

        assertThat(resultats).extracting(ResultatCas::verdict).containsExactly(VerdictCas.REUSSI);
        assertThat(entetesSoumission.get("transfer-encoding")).as("corps envoyé par morceaux").isNull();
        assertThat(entetesSoumission.get("content-length")).as("longueur annoncée")
                .isEqualTo(String.valueOf(corpsSoumission.getBytes(StandardCharsets.UTF_8).length));
        assertThat(corpsSoumission).contains("\"language_id\":71").contains("\"source_code\"");
        assertThat(entetesSoumission.get("x-auth-token")).isEqualTo("jeton");
    }
}
