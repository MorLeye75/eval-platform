package sn.ept.eval.service.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import sn.ept.eval.config.Judge0Proprietes;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Exécute le code d'une réponse sur ses cas de test avec Judge0 (DS-12, phase 2).
 *
 * Le premier cas part seul : une erreur de compilation fait échouer tous les cas sans autre exécution.
 * Les cas restants partent par lots de 20. Les résultats sont interrogés à intervalle croissant
 * (0,5 s, puis ×1,5 jusqu'à 5 s) pour ne pas submerger Judge0.
 *
 * Judge0 indisponible (réseau, délai HTTP, 5xx, statuts 13 et 14, durée maximale dépassée) :
 * {@link Judge0IndisponibleException}, la copie sera recorrigée plus tard. Toute autre anomalie
 * (4xx, réponse illisible) remonte en exception d'exécution : c'est un bug ou une mauvaise configuration.
 */
@Component
public class Judge0Client {

    public record CasAExecuter(Long casId, String entree, String sortieAttendue) {}

    /** Résultat d'un cas : sortie et message tronqués à 10 Ko (contenu étudiant, à échapper à l'affichage). */
    public record ResultatCas(Long casId, boolean reussi, String sortie, double tempsSecondes, String message) {}

    @FunctionalInterface
    interface Attente {
        void attendre(Duration duree) throws InterruptedException;
    }

    static final int TAILLE_LOT = 20;
    static final int TAILLE_MAX_SORTIE = 10 * 1024;
    static final Duration PREMIERE_ATTENTE = Duration.ofMillis(500);
    static final Duration ATTENTE_MAX = Duration.ofSeconds(5);
    private static final String CHAMPS = "token,stdout,stderr,compile_output,message,time,status";

    private static final int EN_FILE = 1;
    private static final int EN_COURS = 2;
    private static final int ACCEPTE = 3;
    private static final int ERREUR_COMPILATION = 6;
    private static final int DERNIER_ECHEC_ETUDIANT = 12; // 4 à 12 : réponse fausse, temps, exécution

    private final RestClient http;
    private final Judge0Proprietes proprietes;
    private final Clock clock;
    private final Attente attente;

    @Autowired
    public Judge0Client(RestClient.Builder builder, Judge0Proprietes proprietes, Clock clock) {
        this(builder.clone().requestFactory(fabriqueAvecDelais(proprietes)), proprietes, clock, Thread::sleep);
    }

    /** Pour les tests : client HTTP simulé et attente instantanée. */
    Judge0Client(RestClient.Builder builder, Judge0Proprietes proprietes, Clock clock, Attente attente) {
        this.proprietes = proprietes;
        this.clock = clock;
        this.attente = attente;
        this.http = builder
                .baseUrl(proprietes.url())
                .defaultHeaders(h -> {
                    if (!proprietes.token().isBlank()) h.set("X-Auth-Token", proprietes.token());
                })
                .build();
    }

    /** Aucun appel ne bloque sans fin : connexion 5 s, lecture 15 s par défaut. */
    private static ClientHttpRequestFactory fabriqueAvecDelais(Judge0Proprietes proprietes) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(proprietes.delaiConnexion()).build();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(client);
        fabrique.setReadTimeout(proprietes.delaiLecture());
        return fabrique;
    }

    public boolean langageConnu(String langage) {
        return langage != null && proprietes.langages().containsKey(langage);
    }

    /**
     * @param echeance fin de la durée accordée à toutes les exécutions de la copie
     * @return un résultat par cas, dans l'ordre des cas
     */
    public List<ResultatCas> executer(String code, String langage, List<CasAExecuter> cas, Instant echeance)
            throws Judge0IndisponibleException {
        Integer languageId = proprietes.langages().get(langage);
        if (languageId == null) throw new IllegalArgumentException("Langage non pris en charge par Judge0 : " + langage);
        if (cas.isEmpty()) return List.of();

        try {
            verifierEcheance(echeance);
            CasAExecuter premier = cas.get(0);
            Jeton jeton = http.post().uri("/submissions?base64_encoded=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(soumission(code, languageId, premier))
                    .retrieve().body(Jeton.class);
            if (jeton == null || jeton.token() == null) throw new IllegalStateException("Judge0 n'a pas renvoyé de jeton");
            ReponseJudge0 reponse = attendreResultat(jeton.token(), echeance);

            if (statut(reponse) == ERREUR_COMPILATION) {
                // Le même code échouerait partout : aucun autre cas n'est exécuté
                String message = tronquer(premierRenseigne(decoder(reponse.compileOutput()), decoder(reponse.message()),
                        description(reponse)));
                return cas.stream().map(c -> new ResultatCas(c.casId(), false, null, 0.0, message)).toList();
            }

            List<ResultatCas> resultats = new ArrayList<>(cas.size());
            resultats.add(resultat(premier, reponse));
            List<CasAExecuter> restants = cas.subList(1, cas.size());
            for (int debut = 0; debut < restants.size(); debut += TAILLE_LOT) {
                List<CasAExecuter> lot = restants.subList(debut, Math.min(debut + TAILLE_LOT, restants.size()));
                resultats.addAll(executerLot(code, languageId, lot, echeance));
            }
            return resultats;
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new Judge0IndisponibleException("Judge0 injoignable ou en erreur : " + e.getMessage(), e);
        }
    }

    private List<ResultatCas> executerLot(String code, int languageId, List<CasAExecuter> lot, Instant echeance)
            throws Judge0IndisponibleException {
        LotSoumissions corps = new LotSoumissions(lot.stream().map(c -> soumission(code, languageId, c)).toList());
        Jeton[] jetons = http.post().uri("/submissions/batch?base64_encoded=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corps)
                .retrieve().body(Jeton[].class);
        if (jetons == null || jetons.length != lot.size() || Arrays.stream(jetons).anyMatch(j -> j == null || j.token() == null)) {
            throw new IllegalStateException("Réponse inattendue de Judge0 à un lot de soumissions");
        }
        String tokens = Arrays.stream(jetons).map(Jeton::token).collect(Collectors.joining(","));

        Duration pause = PREMIERE_ATTENTE;
        while (true) {
            patienter(pause, echeance);
            LotReponses reponses = http.get()
                    .uri("/submissions/batch?tokens={tokens}&base64_encoded=true&fields={champs}", tokens, CHAMPS)
                    .retrieve().body(LotReponses.class);
            if (reponses == null || reponses.submissions() == null || reponses.submissions().size() != lot.size()) {
                throw new IllegalStateException("Réponse inattendue de Judge0 à l'interrogation d'un lot");
            }
            if (reponses.submissions().stream().noneMatch(this::enAttente)) {
                List<ResultatCas> resultats = new ArrayList<>(lot.size());
                for (int i = 0; i < lot.size(); i++) {
                    resultats.add(resultat(lot.get(i), reponses.submissions().get(i)));
                }
                return resultats;
            }
            pause = pauseSuivante(pause);
        }
    }

    private ReponseJudge0 attendreResultat(String token, Instant echeance) throws Judge0IndisponibleException {
        Duration pause = PREMIERE_ATTENTE;
        while (true) {
            patienter(pause, echeance);
            ReponseJudge0 reponse = http.get()
                    .uri("/submissions/{token}?base64_encoded=true&fields={champs}", token, CHAMPS)
                    .retrieve().body(ReponseJudge0.class);
            if (reponse == null) throw new IllegalStateException("Réponse vide de Judge0");
            if (!enAttente(reponse)) return reponse;
            pause = pauseSuivante(pause);
        }
    }

    private ResultatCas resultat(CasAExecuter cas, ReponseJudge0 reponse) throws Judge0IndisponibleException {
        int statut = statut(reponse);
        String sortie = tronquer(decoder(reponse.stdout()));
        if (statut == ACCEPTE) {
            return new ResultatCas(cas.casId(), true, sortie, temps(reponse), null);
        }
        if (statut > ACCEPTE && statut <= DERNIER_ECHEC_ETUDIANT) {
            String message = premierRenseigne(decoder(reponse.compileOutput()), decoder(reponse.stderr()),
                    decoder(reponse.message()), description(reponse));
            return new ResultatCas(cas.casId(), false, sortie, temps(reponse), tronquer(message));
        }
        // 13 « Internal Error », 14 « Exec Format Error » ou statut inconnu : panne de Judge0
        throw new Judge0IndisponibleException("Judge0 : statut " + statut + " (" + description(reponse) + ")");
    }

    private SoumissionJudge0 soumission(String code, int languageId, CasAExecuter cas) {
        return new SoumissionJudge0(languageId, encoder(code), encoder(normaliser(cas.entree())),
                encoder(normaliser(cas.sortieAttendue())), proprietes.cpuTimeLimit(), proprietes.wallTimeLimit(),
                proprietes.memoryLimitKo(), proprietes.maxProcessus(), proprietes.maxFileSizeKo(), false);
    }

    private void patienter(Duration pause, Instant echeance) throws Judge0IndisponibleException {
        if (clock.instant().plus(pause).isAfter(echeance)) {
            throw new Judge0IndisponibleException("Durée maximale des exécutions dépassée");
        }
        try {
            attente.attendre(pause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Judge0IndisponibleException("Exécution interrompue", e);
        }
    }

    private void verifierEcheance(Instant echeance) throws Judge0IndisponibleException {
        if (!clock.instant().isBefore(echeance)) {
            throw new Judge0IndisponibleException("Durée maximale des exécutions dépassée");
        }
    }

    private static Duration pauseSuivante(Duration pause) {
        Duration suivante = Duration.ofMillis(pause.toMillis() * 3 / 2);
        return suivante.compareTo(ATTENTE_MAX) > 0 ? ATTENTE_MAX : suivante;
    }

    private boolean enAttente(ReponseJudge0 reponse) {
        int statut = statut(reponse);
        return statut == EN_FILE || statut == EN_COURS;
    }

    private static int statut(ReponseJudge0 reponse) {
        return reponse.status() == null ? EN_FILE : reponse.status().id();
    }

    private static String description(ReponseJudge0 reponse) {
        return reponse.status() == null ? null : reponse.status().description();
    }

    private static double temps(ReponseJudge0 reponse) {
        if (reponse.time() == null) return 0.0;
        try {
            return Double.parseDouble(reponse.time());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Les fins de ligne Windows saisies par l'enseignant ne doivent pas faire échouer une sortie correcte. */
    static String normaliser(String texte) {
        return texte == null ? null : texte.replace("\r\n", "\n");
    }

    private static String encoder(String texte) {
        return texte == null ? null : Base64.getEncoder().encodeToString(texte.getBytes(StandardCharsets.UTF_8));
    }

    /** Judge0 coupe son base64 en lignes de 60 caractères : décodeur MIME, qui ignore les retours à la ligne. */
    static String decoder(String base64) {
        if (base64 == null) return null;
        try {
            return new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return base64;
        }
    }

    static String tronquer(String texte) {
        if (texte == null || texte.length() <= TAILLE_MAX_SORTIE) return texte;
        int fin = TAILLE_MAX_SORTIE;
        if (Character.isHighSurrogate(texte.charAt(fin - 1))) fin--; // ne pas couper un caractère en deux
        return texte.substring(0, fin) + "\n… (tronqué)";
    }

    private static String premierRenseigne(String... textes) {
        return Arrays.stream(textes).filter(Objects::nonNull).filter(t -> !t.isBlank()).findFirst().orElse(null);
    }

    // --- Format JSON de l'API Judge0 ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SoumissionJudge0(
            @JsonProperty("language_id") int languageId,
            @JsonProperty("source_code") String sourceCode,
            @JsonProperty("stdin") String stdin,
            @JsonProperty("expected_output") String expectedOutput,
            @JsonProperty("cpu_time_limit") double cpuTimeLimit,
            @JsonProperty("wall_time_limit") double wallTimeLimit,
            @JsonProperty("memory_limit") int memoryLimit,
            @JsonProperty("max_processes_and_or_threads") int maxProcessesAndOrThreads,
            @JsonProperty("max_file_size") int maxFileSize,
            @JsonProperty("enable_network") boolean enableNetwork) {}

    record LotSoumissions(List<SoumissionJudge0> submissions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Jeton(String token) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatutJudge0(int id, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReponseJudge0(
            String token,
            String stdout,
            String stderr,
            @JsonProperty("compile_output") String compileOutput,
            String message,
            String time,
            StatutJudge0 status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LotReponses(List<ReponseJudge0> submissions) {}
}
