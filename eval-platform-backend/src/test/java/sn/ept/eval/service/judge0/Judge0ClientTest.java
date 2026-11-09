package sn.ept.eval.service.judge0;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import sn.ept.eval.config.Judge0Proprietes;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.service.judge0.Judge0Client.CasAExecuter;
import sn.ept.eval.service.judge0.Judge0Client.ResultatCas;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Dialogue avec Judge0 (DS-12, phase 2) sur un serveur HTTP simulé : premier cas seul puis lots, interrogation
 * à intervalle croissant, erreur de compilation sans autre exécution, et tout ce qui compte comme
 * « Judge0 indisponible » (statut 13, 5xx, réseau, durée maximale dépassée).
 */
class Judge0ClientTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-08T10:00:00Z");
    private static final String BASE = "http://judge0:2358";

    private final Clock horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    private final List<Duration> pauses = new ArrayList<>();
    private MockRestServiceServer serveur;
    private Judge0Client client;

    private static Judge0Proprietes proprietes() {
        return new Judge0Proprietes(BASE, "jeton-secret", 2, 2, 5, 256000, 60, 1024,
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(10), null);
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(builder).build();
        client = new Judge0Client(builder, proprietes(), horloge, pauses::add);
    }

    private static String b64(String texte) {
        return Base64.getEncoder().encodeToString(texte.getBytes(StandardCharsets.UTF_8));
    }

    private static String resultat(int statut, String stdout) {
        return "{\"stdout\":" + (stdout == null ? "null" : "\"" + b64(stdout) + "\"")
                + ",\"time\":\"0.012\",\"stderr\":null,\"compile_output\":null,\"message\":null,"
                + "\"status\":{\"id\":" + statut + ",\"description\":\"statut " + statut + "\"}}";
    }

    private static List<CasAExecuter> cas(int nombre) {
        return IntStream.rangeClosed(1, nombre)
                .mapToObj(i -> new CasAExecuter((long) i, "entree " + i, "sortie " + i)).toList();
    }

    private Instant dans10Minutes() {
        return MAINTENANT.plus(Duration.ofMinutes(10));
    }

    private void attendrePremierCas(String token) {
        serveur.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"token\":\"" + token + "\"}"));
    }

    @Test
    @DisplayName("Premier cas seul puis lot des suivants : réussi (3) ou échoué (4), interrogation à intervalle croissant")
    void premierCasPuisLot() throws Exception {
        serveur.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Auth-Token", "jeton-secret"))
                .andExpect(jsonPath("$.language_id").value(71))
                .andExpect(jsonPath("$.source_code").value(b64("print(input())")))
                .andExpect(jsonPath("$.stdin").value(b64("radar\n")))
                // Fins de ligne Windows saisies par l'enseignant normalisées
                .andExpect(jsonPath("$.expected_output").value(b64("radar\n")))
                .andExpect(jsonPath("$.wall_time_limit").value(5.0))
                .andExpect(jsonPath("$.enable_network").value(false))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"token\":\"t1\"}"));
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t1?base64_encoded=true")))
                .andRespond(withSuccess(resultat(2, null), MediaType.APPLICATION_JSON));
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t1?base64_encoded=true")))
                .andRespond(withSuccess(resultat(3, "radar\n"), MediaType.APPLICATION_JSON));
        serveur.expect(once(), requestTo(BASE + "/submissions/batch?base64_encoded=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.submissions.length()").value(2))
                .andRespond(withSuccess("[{\"token\":\"t2\"},{\"token\":\"t3\"}]", MediaType.APPLICATION_JSON));
        serveur.expect(once(), requestTo(containsString("/submissions/batch?tokens=t2")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"submissions\":[" + resultat(3, "x") + "," + resultat(1, null) + "]}",
                        MediaType.APPLICATION_JSON));
        serveur.expect(once(), requestTo(containsString("/submissions/batch?tokens=t2")))
                .andRespond(withSuccess("{\"submissions\":[" + resultat(3, "kayak\n") + "," + resultat(4, "bonjour\n") + "]}",
                        MediaType.APPLICATION_JSON));

        List<CasAExecuter> cas = List.of(
                new CasAExecuter(1L, "radar\r\n", "radar\r\n"),
                new CasAExecuter(2L, "kayak", "kayak"),
                new CasAExecuter(3L, "bonjour", "ruojnob"));
        List<ResultatCas> resultats = client.executer("print(input())", "python", cas, dans10Minutes());

        serveur.verify();
        assertThat(resultats).extracting(ResultatCas::casId).containsExactly(1L, 2L, 3L);
        assertThat(resultats).extracting(ResultatCas::reussi).containsExactly(true, true, false);
        assertThat(resultats.get(0).sortie()).isEqualTo("radar\n");
        assertThat(resultats.get(0).tempsSecondes()).isEqualTo(0.012);
        assertThat(resultats.get(2).message()).isEqualTo("statut 4");
        assertThat(resultats).extracting(ResultatCas::verdict)
                .containsExactly(VerdictCas.REUSSI, VerdictCas.REUSSI, VerdictCas.MAUVAISE_REPONSE);
        // 0,5 s puis ×1,5 pour chaque interrogation, remis à 0,5 s pour le lot
        assertThat(pauses).containsExactly(Duration.ofMillis(500), Duration.ofMillis(750),
                Duration.ofMillis(500), Duration.ofMillis(750));
    }

    @Test
    @DisplayName("Lot : la liste des jetons arrive à Judge0 séparée par des virgules une fois l'URL décodée (pas de double encodage)")
    void lot_JetonsSeparesParDesVirgules() throws Exception {
        attendrePremierCas("t1");
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t1")))
                .andRespond(withSuccess(resultat(3, "ok"), MediaType.APPLICATION_JSON));
        serveur.expect(once(), requestTo(BASE + "/submissions/batch?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"a1-b2\"},{\"token\":\"c3-d4\"}]", MediaType.APPLICATION_JSON));
        serveur.expect(once(), requestTo(containsString("/submissions/batch?tokens=")))
                .andExpect(requete -> {
                    String brute = requete.getURI().getRawQuery();
                    // Ce que fait Rails (Rack) avant le split(",") de SubmissionsController#batch_show
                    java.util.Map<String, String> params = java.util.Arrays.stream(brute.split("&"))
                            .map(p -> p.split("=", 2))
                            .collect(Collectors.toMap(p -> p[0],
                                    p -> java.net.URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
                    assertThat(params.get("tokens").split(",")).containsExactly("a1-b2", "c3-d4");
                    assertThat(params.get("base64_encoded")).isEqualTo("true");
                    assertThat(params.get("fields").split(",")).contains("status", "stdout", "compile_output", "message");
                })
                .andRespond(withSuccess("{\"submissions\":[" + resultat(3, "ok") + "," + resultat(3, "ok") + "]}",
                        MediaType.APPLICATION_JSON));

        client.executer("code", "python", cas(3), dans10Minutes());

        serveur.verify(); // envoyé : ?tokens=a1-b2%2Cc3-d4&base64_encoded=true&fields=token%2Cstdout…
    }

    @Test
    @DisplayName("Erreur de compilation sur le premier cas : tous les cas échouent avec le message, rien d'autre n'est exécuté")
    void erreurCompilation_TousEchoues() throws Exception {
        attendrePremierCas("t1");
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t1")))
                .andRespond(withSuccess("{\"compile_output\":\"" + b64("main.c:1: erreur : ';' attendu") + "\","
                        + "\"status\":{\"id\":6,\"description\":\"Compilation Error\"}}", MediaType.APPLICATION_JSON));

        List<ResultatCas> resultats = client.executer("int main( {", "c", cas(3), dans10Minutes());

        serveur.verify(); // aucun lot envoyé
        assertThat(resultats).hasSize(3).allSatisfy(r -> {
            assertThat(r.reussi()).isFalse();
            assertThat(r.message()).isEqualTo("main.c:1: erreur : ';' attendu");
            assertThat(r.verdict()).isEqualTo(VerdictCas.ERREUR_COMPILATION);
        });
    }

    @Test
    @DisplayName("Plus de 20 cas : lots de 20 au plus")
    void lotsDe20() throws Exception {
        attendrePremierCas("t0");
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t0")))
                .andRespond(withSuccess(resultat(3, "ok"), MediaType.APPLICATION_JSON));
        for (int taille : new int[]{20, 2}) {
            String jetons = IntStream.range(0, taille).mapToObj(i -> "{\"token\":\"l" + taille + "-" + i + "\"}")
                    .collect(Collectors.joining(",", "[", "]"));
            String resultats = IntStream.range(0, taille).mapToObj(i -> resultat(3, "ok"))
                    .collect(Collectors.joining(",", "{\"submissions\":[", "]}"));
            serveur.expect(once(), requestTo(BASE + "/submissions/batch?base64_encoded=true"))
                    .andExpect(jsonPath("$.submissions.length()").value(taille))
                    .andRespond(withSuccess(jetons, MediaType.APPLICATION_JSON));
            serveur.expect(once(), requestTo(containsString("/submissions/batch?tokens=l" + taille + "-0")))
                    .andRespond(withSuccess(resultats, MediaType.APPLICATION_JSON));
        }

        List<ResultatCas> resultats = client.executer("code", "java", cas(23), dans10Minutes());

        serveur.verify();
        assertThat(resultats).hasSize(23).allMatch(ResultatCas::reussi);
        assertThat(resultats).extracting(ResultatCas::casId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 23).mapToObj(i -> (long) i).toList());
    }

    @Test
    @DisplayName("Statut 13 « Internal Error » : Judge0 indisponible (pas une erreur de l'étudiant)")
    void statut13_Indisponible() {
        attendrePremierCas("t1");
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t1")))
                .andRespond(withSuccess(resultat(13, null), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.executer("code", "python", cas(1), dans10Minutes()))
                .isInstanceOf(Judge0IndisponibleException.class);
    }

    @Test
    @DisplayName("Erreur 503 de Judge0 (file pleine) : indisponible")
    void erreur5xx_Indisponible() {
        serveur.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.executer("code", "python", cas(1), dans10Minutes()))
                .isInstanceOf(Judge0IndisponibleException.class);
    }

    @Test
    @DisplayName("Judge0 injoignable ou délai de lecture dépassé : indisponible")
    void reseau_Indisponible() {
        serveur.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.executer("code", "python", cas(1), dans10Minutes()))
                .isInstanceOf(Judge0IndisponibleException.class);
    }

    @Test
    @DisplayName("Erreur 4xx (jeton refusé, requête invalide) : bug ou configuration, pas une panne")
    void erreur4xx_Propagee() {
        serveur.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.executer("code", "python", cas(1), dans10Minutes()))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(Judge0IndisponibleException.class);
    }

    @Test
    @DisplayName("Durée maximale dépassée pendant l'attente du résultat : indisponible, pas d'attente sans fin")
    void dureeMaximale_PendantLAttente() {
        attendrePremierCas("t1");
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t1")))
                .andRespond(withSuccess(resultat(1, null), MediaType.APPLICATION_JSON));
        serveur.expect(once(), requestTo(startsWith(BASE + "/submissions/t1")))
                .andRespond(withSuccess(resultat(2, null), MediaType.APPLICATION_JSON));

        // 0,5 s + 0,75 s passent, la pause suivante (1,125 s) dépasserait l'échéance d'une seconde
        assertThatThrownBy(() -> client.executer("code", "python", cas(1), MAINTENANT.plusSeconds(1)))
                .isInstanceOf(Judge0IndisponibleException.class);
        serveur.verify();
    }

    @Test
    @DisplayName("Durée maximale déjà dépassée : aucun appel à Judge0")
    void dureeMaximale_DejaDepassee() {
        assertThatThrownBy(() -> client.executer("code", "python", cas(1), MAINTENANT))
                .isInstanceOf(Judge0IndisponibleException.class);
        serveur.verify();
    }

    @Test
    @DisplayName("Langages : ceux de l'éditeur sont connus, un langage absent ne l'est pas")
    void langages() {
        assertThat(List.of("c", "cpp", "java", "python", "javascript")).allMatch(client::langageConnu);
        assertThat(client.langageConnu(null)).isFalse();
        assertThat(client.langageConnu("cobol")).isFalse();
    }

    @Test
    @DisplayName("Base64 de Judge0 coupé en lignes de 60 caractères, sorties tronquées à 10 Ko")
    void decodageEtTroncature() throws IOException {
        String long64 = b64("é".repeat(100));
        String avecRetours = long64.substring(0, 60) + "\n" + long64.substring(60);
        assertThat(Judge0Client.decoder(avecRetours)).isEqualTo("é".repeat(100));

        String sortie = "x".repeat(Judge0Client.TAILLE_MAX_SORTIE + 500);
        assertThat(Judge0Client.tronquer(sortie)).startsWith("x".repeat(Judge0Client.TAILLE_MAX_SORTIE))
                .endsWith("(tronqué)").hasSizeLessThan(Judge0Client.TAILLE_MAX_SORTIE + 20);
        assertThat(Judge0Client.tronquer("court")).isEqualTo("court");
    }
}
