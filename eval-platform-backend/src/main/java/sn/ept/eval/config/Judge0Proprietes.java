package sn.ept.eval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Exécution du code des étudiants par Judge0 auto-hébergé (DS-12). Judge0 reste sur le réseau interne :
 * il n'est jamais exposé publiquement.
 *
 * @param url            adresse de Judge0 (JUDGE0_URL)
 * @param token          jeton d'authentification, en-tête X-Auth-Token (JUDGE0_TOKEN), vide si Judge0 n'en exige pas
 * @param concurrence    corrections avec du code lancées en même temps : le nombre de workers Judge0
 * @param langages       langage de la réponse (V6) -> language_id de Judge0
 * @param dureeMaxExecution durée maximale de toutes les exécutions d'une copie (phase 2), sous les 15 min
 *                       de prise en charge
 */
@ConfigurationProperties("correction.judge0")
public record Judge0Proprietes(
        @DefaultValue("http://localhost:2358") String url,
        @DefaultValue("") String token,
        @DefaultValue("2") int concurrence,
        @DefaultValue("2") double cpuTimeLimit,
        @DefaultValue("5") double wallTimeLimit,
        @DefaultValue("256000") int memoryLimitKo,
        @DefaultValue("60") int maxProcessus,
        @DefaultValue("1024") int maxFileSizeKo,
        @DefaultValue("5s") Duration delaiConnexion,
        @DefaultValue("15s") Duration delaiLecture,
        @DefaultValue("10m") Duration dureeMaxExecution,
        Map<String, Integer> langages) {

    /** Identifiants de Judge0 CE 1.13 : C (GCC 9.2), C++ (GCC 9.2), Java (OpenJDK 13), Python 3.8, Node.js 12. */
    public static final Map<String, Integer> LANGAGES_PAR_DEFAUT =
            Map.of("c", 50, "cpp", 54, "java", 62, "python", 71, "javascript", 63);

    public Judge0Proprietes {
        if (langages == null || langages.isEmpty()) langages = LANGAGES_PAR_DEFAUT;
        if (concurrence < 1) throw new IllegalArgumentException("correction.judge0.concurrence doit valoir au moins 1");
    }
}
