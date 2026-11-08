package sn.ept.eval.service;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correction asynchrone qui échoue en cours de route : rien ne doit être enregistré (pas de notes partielles),
 * la copie reste SOUMIS et une nouvelle correction (relance planifiée) aboutit.
 * Base H2 propre à cette classe ; la panne est simulée par un StatementInspector.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:correction;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "sn.ept.eval.service.CorrectionIntegrationTest$PanneSimulee"
})
class CorrectionIntegrationTest {

    /**
     * Fait échouer une fois le chargement des questions de l'évaluation, que la correction effectue
     * après avoir noté les réponses QCM (notes déjà posées sur les entités, pas encore validées).
     */
    public static class PanneSimulee implements StatementInspector {
        static final AtomicBoolean ARMEE = new AtomicBoolean(false);
        static final AtomicBoolean DECLENCHEE = new AtomicBoolean(false);

        @Override
        public String inspect(String sql) {
            String s = sql.toLowerCase(Locale.ROOT);
            if (s.startsWith("select") && s.contains(" from questions ") && s.contains("evaluation_id=?")
                    && Thread.currentThread().getName().startsWith("CorrectionAsync-")
                    && ARMEE.compareAndSet(true, false)) {
                DECLENCHEE.set(true);
                throw new IllegalStateException("Panne simulée pendant la correction");
            }
            return sql;
        }
    }

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private CorrectionService correctionService;

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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("correctionTaskExecutor")
    private Executor correctionTaskExecutor;

    @AfterEach
    void desarmer() {
        PanneSimulee.ARMEE.set(false);
        PanneSimulee.DECLENCHEE.set(false);
    }

    /** Attend que les corrections en file et en cours soient terminées. */
    private void attendreCorrections() throws InterruptedException {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) correctionTaskExecutor;
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite
                && (executor.getActiveCount() > 0 || !executor.getThreadPoolExecutor().getQueue().isEmpty())) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }

    @Test
    @DisplayName("Panne en cours de correction : aucune note partielle enregistrée, puis la relance corrige la copie")
    void panneEnCoursDeCorrection_RienNEstEnregistre() throws Exception {
        Long evalQcmId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("QCM")).map(Evaluation::getId).findFirst().orElseThrow();
        String suffixe = UUID.randomUUID().toString();
        String email = "etu-" + suffixe + "@ept.sn";
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Etudiant", email, "x", "ETU-" + suffixe, dic2));
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, email).getId();
        List<ReponseItemDto> correctes = transactionTemplate.execute(tx ->
                questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQcmId).stream()
                        .map(q -> new ReponseItemDto(q.getId(), null, null, ((QuestionQCM) q).getChoix().stream()
                                .filter(ChoixReponse::getEstCorrect).map(ChoixReponse::getId).toList()))
                        .toList());
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(correctes), email);

        PanneSimulee.ARMEE.set(true);
        soumissionService.soumettreCopie(soumissionId, email);
        attendreCorrections();

        assertThat(PanneSimulee.DECLENCHEE).isTrue();
        assertThat(soumissionRepository.findById(soumissionId).orElseThrow().getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        List<Double> notes = jdbcTemplate.queryForList(
                "select note_obtenue from reponses_etudiant where soumission_id = ?", Double.class, soumissionId);
        assertThat(notes).isNotEmpty().containsOnlyNulls();
        // DS-12 : l'échec est compté et la copie n'est pas reprise avant 1 min
        Soumission enEchec = soumissionRepository.findById(soumissionId).orElseThrow();
        assertThat(enEchec.getTentativesCorrection()).isEqualTo(1);
        assertThat(enEchec.getCorrectionDemarreeLe()).isNull();
        assertThat(enEchec.getProchaineTentativeLe()).isAfter(java.time.LocalDateTime.now().plusSeconds(30));

        // Relance (tâche planifiée) : la correction aboutit
        correctionService.corrigerSoumissionAsync(soumissionId).get(10, TimeUnit.SECONDS);
        Soumission corrigee = soumissionRepository.findById(soumissionId).orElseThrow();
        assertThat(corrigee.getStatut()).isEqualTo(StatutSoumission.EVALUE);
        assertThat(corrigee.getNoteFinale()).isEqualTo(5.0);
    }
}
