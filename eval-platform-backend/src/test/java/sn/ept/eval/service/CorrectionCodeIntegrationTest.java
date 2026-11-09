package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.repository.*;
import sn.ept.eval.service.judge0.Judge0Client;
import sn.ept.eval.service.judge0.Judge0Client.CasAExecuter;
import sn.ept.eval.service.judge0.Judge0Client.ResultatCas;
import sn.ept.eval.service.judge0.Judge0IndisponibleException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Correction du code de bout en bout (DS-12) sur H2, Judge0 simulé : remise, exécuteur Judge0, écriture des
 * résultats par cas, tentative comptée si Judge0 est indisponible, et annulation réelle d'une erreur
 * survenue après la prise en charge.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:correctioncode;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class CorrectionCodeIntegrationTest {

    private static final String CODE = "s = input()\nprint('true' if s == s[::-1] else 'false')";

    @MockitoBean
    private Judge0Client judge0;

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private ClotureCopiesService clotureCopiesService;

    @Autowired
    private CorrectionsEnFile enFile;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long evalCodeId;
    private Long questionId;
    private String email;

    @BeforeEach
    void setUp() {
        evalCodeId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("code")).map(Evaluation::getId).findFirst().orElseThrow();
        questionId = questionRepository.findByEvaluationIdOrderByOrdreAsc(evalCodeId).get(0).getId();
        String suffixe = UUID.randomUUID().toString();
        email = "etu-" + suffixe + "@ept.sn";
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Etudiant", email, "x", "ETU-" + suffixe, dic2));
        when(judge0.langageConnu("python")).thenReturn(true);
    }

    /** Démarre une copie, enregistre le code en Python et la remet. */
    private Long remettreCode() throws InterruptedException {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalCodeId, email).getId();
        ReponseItemDto reponse = new ReponseItemDto(questionId, null, CODE, List.of());
        reponse.setLangage("python");
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(List.of(reponse)), email);
        soumissionService.soumettreCopie(soumissionId, email);
        attendreFinCorrection(soumissionId);
        return soumissionId;
    }

    /** La copie quitte les copies en file à la toute fin de sa correction (phase 3 validée). */
    private void attendreFinCorrection(Long soumissionId) throws InterruptedException {
        for (int i = 0; i < 100 && enFile.contient(soumissionId); i++) {
            Thread.sleep(50);
        }
        assertThat(enFile.contient(soumissionId)).as("correction terminée").isFalse();
    }

    private Soumission copie(Long id) {
        return soumissionRepository.findById(id).orElseThrow();
    }

    private Integer nbResultatsCasTests(Long soumissionId) {
        return jdbcTemplate.queryForObject("select count(*) from resultats_cas_tests r join reponses_etudiant e "
                + "on e.id = r.reponse_code_id where e.soumission_id = ?", Integer.class, soumissionId);
    }

    @Test
    @DisplayName("Remise d'un code : exécuté sur l'exécuteur Judge0, 2 cas sur 3 réussis → 3,33/5, copie EVALUE")
    void codeCorrigeParJudge0() throws Exception {
        AtomicReference<String> thread = new AtomicReference<>();
        when(judge0.executer(eq(CODE), eq("python"), anyList(), any())).thenAnswer(inv -> {
            thread.set(Thread.currentThread().getName());
            List<CasAExecuter> cas = inv.getArgument(2);
            return List.of(
                    new ResultatCas(cas.get(0).casId(), VerdictCas.REUSSI, "true\n", 0.02, null),
                    new ResultatCas(cas.get(1).casId(), VerdictCas.REUSSI, "false\n", 0.02, null),
                    new ResultatCas(cas.get(2).casId(), VerdictCas.MAUVAISE_REPONSE, "false\n", 0.02, "Wrong Answer")); // cas masqué
        });

        Long soumissionId = remettreCode();

        Soumission corrigee = copie(soumissionId);
        assertThat(thread.get()).startsWith("CorrectionJudge0-");
        assertThat(corrigee.getStatut()).isEqualTo(StatutSoumission.EVALUE);
        assertThat(corrigee.getNoteFinale()).isEqualTo(3.33);
        assertThat(corrigee.getTentativesCorrection()).isZero();
        assertThat(corrigee.getCorrectionDemarreeLe()).isNull();
        assertThat(nbResultatsCasTests(soumissionId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("select r.verdict from resultats_cas_tests r join reponses_etudiant e "
                + "on e.id = r.reponse_code_id where e.soumission_id = ? order by r.id", String.class, soumissionId))
                .containsExactly("REUSSI", "REUSSI", "MAUVAISE_REPONSE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from resultats_cas_tests r join reponses_etudiant e "
                + "on e.id = r.reponse_code_id where e.soumission_id = ? and r.est_valide", Integer.class, soumissionId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Judge0 indisponible : copie SOUMIS, tentative comptée, pas de reprise par la relance avant 1 min")
    void judge0Indisponible_TentativeComptee() throws Exception {
        when(judge0.executer(any(), any(), anyList(), any())).thenThrow(new Judge0IndisponibleException("panne"));

        Long soumissionId = remettreCode();

        Soumission copie = copie(soumissionId);
        assertThat(copie.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(copie.getTentativesCorrection()).isEqualTo(1);
        assertThat(copie.getCorrectionDemarreeLe()).isNull();
        assertThat(copie.getProchaineTentativeLe())
                .isBetween(LocalDateTime.now().plusSeconds(30), LocalDateTime.now().plusSeconds(61));

        LocalDateTime prochaine = copie.getProchaineTentativeLe();
        clotureCopiesService.relancerCorrectionsBloquees();
        assertThat(copie(soumissionId).getProchaineTentativeLe()).isEqualTo(prochaine); // non reprise
        assertThat(enFile.contient(soumissionId)).isFalse();
    }

    @Test
    @DisplayName("Erreur imprévue après la prise en charge : rien n'est écrit (annulation réelle), tentative comptée")
    void erreurApresPriseEnCharge_RienNEstEcrit() throws Exception {
        // Résultat pour un cas de test inconnu : l'écriture (phase 3) échoue après les premières notes
        when(judge0.executer(any(), any(), anyList(), any()))
                .thenReturn(List.of(new ResultatCas(-1L, VerdictCas.REUSSI, "true\n", 0.01, null)));

        Long soumissionId = remettreCode();

        Soumission copie = copie(soumissionId);
        assertThat(copie.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(copie.getTentativesCorrection()).isEqualTo(1); // jeton relu en base : l'échec est bien le sien
        assertThat(copie.getCorrectionDemarreeLe()).isNull();
        assertThat(copie.getProchaineTentativeLe()).isAfter(LocalDateTime.now());
        assertThat(nbResultatsCasTests(soumissionId)).isZero();
        assertThat(jdbcTemplate.queryForList("select note_obtenue from reponses_etudiant where soumission_id = ?",
                Double.class, soumissionId)).containsOnlyNulls();
    }
}
