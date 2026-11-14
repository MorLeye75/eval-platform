package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correction de bout en bout contre un VRAI Judge0 (DS-12) : remise d'une copie de code, exécution dans le bac à
 * sable, verdicts et note. Ignoré sans Judge0 (CI) ; à lancer sur un hôte en cgroups v1 :
 * <pre>JUDGE0_REEL_URL=http://localhost:2358 JUDGE0_REEL_TOKEN=... mvn test -Dtest=Judge0ReelIntegrationTest</pre>
 * Hôte lent (VM sans accélération matérielle) : JUDGE0_REEL_DELAI_LECTURE (ex. 60s), JUDGE0_REEL_DUREE_MAX (ex. 30m)
 * et JUDGE0_REEL_ATTENTE_MINUTES (ex. 30) allongent les délais ; par défaut, ceux de la production et 5 min.
 * Question sur 3 points, 3 cas (2 visibles, 1 masqué) : un point par cas réussi. Base H2 propre à cette classe.
 */
@EnabledIfEnvironmentVariable(named = "JUDGE0_REEL_URL", matches = ".+")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:judge0-reel;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class Judge0ReelIntegrationTest {

    @DynamicPropertySource
    static void judge0Reel(DynamicPropertyRegistry registry) {
        registry.add("correction.judge0.url", () -> System.getenv("JUDGE0_REEL_URL"));
        registry.add("correction.judge0.token", () -> System.getenv().getOrDefault("JUDGE0_REEL_TOKEN", ""));
        registry.add("correction.judge0.delai-lecture", () -> System.getenv().getOrDefault("JUDGE0_REEL_DELAI_LECTURE", "15s"));
        registry.add("correction.judge0.duree-max-execution", () -> System.getenv().getOrDefault("JUDGE0_REEL_DUREE_MAX", "10m"));
    }

    /** Attente maximale d'une correction : sans relance planifiée dans les tests, un essai raté laisse la copie SOUMIS. */
    private static final long ATTENTE_MS =
            Long.parseLong(System.getenv().getOrDefault("JUDGE0_REEL_ATTENTE_MINUTES", "5")) * 60_000L;

    @Autowired private SoumissionService soumissionService;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long evaluationId;
    private Long questionId;

    @BeforeEach
    void setUp() {
        Promotion promo = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        evaluationId = transactionTemplate.execute(tx -> {
            Evaluation e = new Evaluation();
            e.setTitre("Palindrome " + UUID.randomUUID());
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusHours(1));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            e.setPromotions(new HashSet<>(Set.of(promo)));
            QuestionCode q = new QuestionCode("Afficher true si l'entrée est un palindrome, false sinon.", 1, 3.0);
            q.addCasDeTest(new CasDeTest("radar", "true", false));
            q.addCasDeTest(new CasDeTest("bonjour", "false", false));
            q.addCasDeTest(new CasDeTest("kayak", "true", true));
            e.addQuestion(q);
            return evaluationRepository.save(e).getId();
        });
        questionId = questionRepository.findByEvaluationIdOrderByOrdreAsc(evaluationId).get(0).getId();
    }

    private record Correction(StatutSoumission statut, Double note, List<VerdictCas> verdicts) {}

    /** Remise par la vraie passation, puis attente de la correction (au plus ATTENTE_MS). */
    private Correction corriger(String langage, String code) throws InterruptedException {
        String email = "etu-j0-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Judge0", email, passwordEncoder.encode("x"),
                "ETU-" + UUID.randomUUID(), promotionRepository.findByNom("DIC2 Informatique").orElseThrow()));
        Long id = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        ReponseItemDto reponse = new ReponseItemDto(questionId, null, code, List.of());
        reponse.setLangage(langage);
        soumissionService.sauvegarderReponses(id, new SauvegardeReponsesRequest(List.of(reponse)), email);
        soumissionService.soumettreCopie(id, email);

        long limite = System.currentTimeMillis() + ATTENTE_MS;
        while (System.currentTimeMillis() < limite
                && soumissionRepository.findById(id).orElseThrow().getStatut() == StatutSoumission.SOUMIS) {
            Thread.sleep(1000);
        }
        return transactionTemplate.execute(tx -> {
            Soumission s = soumissionRepository.findById(id).orElseThrow();
            ReponseCode rc = (ReponseCode) org.hibernate.Hibernate.unproxy(s.getReponses().get(0));
            List<VerdictCas> verdicts = rc.getResultatsCasTests().stream()
                    .sorted(java.util.Comparator.comparing(r -> r.getCasDeTest().getId()))
                    .map(ResultatCasTest::getVerdict).toList();
            return new Correction(s.getStatut(), rc.getNoteObtenue(), verdicts);
        });
    }

    private static final String PALINDROME_PYTHON = "s = input()\nprint('true' if s == s[::-1] else 'false')\n";

    @Test
    @DisplayName("Python juste : 3 cas réussis, 3/3, copie notée")
    void pythonJuste() throws Exception {
        Correction c = corriger("python", PALINDROME_PYTHON);

        assertThat(c.statut()).isEqualTo(StatutSoumission.EVALUE);
        assertThat(c.verdicts()).containsExactly(VerdictCas.REUSSI, VerdictCas.REUSSI, VerdictCas.REUSSI);
        assertThat(c.note()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Python faux (affiche toujours true) : « bonjour » en mauvaise réponse, 2/3")
    void pythonFaux() throws Exception {
        Correction c = corriger("python", "input()\nprint('true')\n");

        assertThat(c.verdicts()).containsExactly(VerdictCas.REUSSI, VerdictCas.MAUVAISE_REPONSE, VerdictCas.REUSSI);
        assertThat(c.note()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Java juste (classe Main) : 3/3")
    void javaJuste() throws Exception {
        Correction c = corriger("java", """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        String s = new Scanner(System.in).nextLine();
                        System.out.println(new StringBuilder(s).reverse().toString().equals(s) ? "true" : "false");
                    }
                }
                """);

        assertThat(c.verdicts()).containsExactly(VerdictCas.REUSSI, VerdictCas.REUSSI, VerdictCas.REUSSI);
        assertThat(c.note()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("C qui ne compile pas : erreur de compilation sur tous les cas, 0/3")
    void cErreurCompilation() throws Exception {
        Correction c = corriger("c", "int main( { return 0; }\n");

        assertThat(c.verdicts()).containsOnly(VerdictCas.ERREUR_COMPILATION).hasSize(3);
        assertThat(c.note()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Python qui plante : erreur à l'exécution, 0/3")
    void pythonErreurExecution() throws Exception {
        Correction c = corriger("python", "raise ValueError('oups')\n");

        assertThat(c.verdicts()).containsOnly(VerdictCas.ERREUR_EXECUTION).hasSize(3);
        assertThat(c.note()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Boucle infinie : temps dépassé (limite de 2 s de CPU), 0/3, sans bloquer la correction")
    void boucleInfinie() throws Exception {
        Correction c = corriger("python", "while True:\n    pass\n");

        assertThat(c.verdicts()).containsOnly(VerdictCas.TEMPS_DEPASSE).hasSize(3);
        assertThat(c.note()).isEqualTo(0.0);
    }
}
