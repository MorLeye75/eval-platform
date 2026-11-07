package sn.ept.eval.service;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clôture automatique des copies expirées (tâche planifiée) : coût en requêtes et isolation des clôtures.
 * Base H2 propre à cette classe ; les requêtes SQL sur amenagements_temps sont comptées par un StatementInspector.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cloture;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "sn.ept.eval.service.ClotureCopiesIntegrationTest$CompteurRequetes"
})
class ClotureCopiesIntegrationTest {

    /** Compte les requêtes SQL qui lisent la table des aménagements de temps. */
    public static class CompteurRequetes implements StatementInspector {
        static final AtomicInteger AMENAGEMENTS = new AtomicInteger();

        @Override
        public String inspect(String sql) {
            if (sql.toLowerCase(Locale.ROOT).contains("amenagements_temps")) AMENAGEMENTS.incrementAndGet();
            return sql;
        }
    }

    @Autowired
    private ClotureCopiesService clotureCopiesService;

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long evalQcmId;

    @BeforeEach
    void setUp() {
        evalQcmId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("QCM")).map(Evaluation::getId).findFirst().orElseThrow();
    }

    /** Copie EN_COURS d'un nouvel étudiant (une seule copie par étudiant et par évaluation). */
    private Long nouvelleCopie() {
        String suffixe = UUID.randomUUID().toString();
        String email = "etu-" + suffixe + "@ept.sn";
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Etudiant", email, "x", "ETU-" + suffixe, dic2));
        return soumissionService.demarrerOuReprendre(evalQcmId, email).getId();
    }

    private void expirer(Long soumissionId) {
        jdbcTemplate.update("update soumissions set date_debut = ? where id = ?", LocalDateTime.now().minusMinutes(60), soumissionId);
    }

    private StatutSoumission statut(Long soumissionId) {
        return soumissionRepository.findById(soumissionId).orElseThrow().getStatut();
    }

    @Test
    @DisplayName("Le nombre de requêtes sur les aménagements ne dépend pas du nombre de copies en cours")
    void requetesAmenagements_IndependantesDuNombreDeCopies() {
        nouvelleCopie();
        clotureCopiesService.cloturerCopiesExpirees();
        CompteurRequetes.AMENAGEMENTS.set(0);
        clotureCopiesService.cloturerCopiesExpirees();
        int avecUneCopie = CompteurRequetes.AMENAGEMENTS.get();

        for (int i = 0; i < 5; i++) nouvelleCopie();
        CompteurRequetes.AMENAGEMENTS.set(0);
        clotureCopiesService.cloturerCopiesExpirees();

        assertThat(CompteurRequetes.AMENAGEMENTS.get()).isEqualTo(avecUneCopie).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Une copie verrouillée par une autre transaction n'empêche pas la clôture des autres copies expirées")
    void copieVerrouilleeAilleurs_NeBloquePasLesAutres() throws Exception {
        Long bloquee = nouvelleCopie();
        Long libre = nouvelleCopie();
        expirer(bloquee);
        expirer(libre);

        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Une autre transaction (soumission, reprise…) détient le verrou sur la copie « bloquee »
            Future<?> detenteur = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                soumissionRepository.findByIdForUpdate(bloquee).orElseThrow();
                verrouPris.countDown();
                try {
                    liberer.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(verrouPris.await(10, TimeUnit.SECONDS)).isTrue();

            clotureCopiesService.cloturerCopiesExpirees();

            assertThat(statut(libre)).isNotEqualTo(StatutSoumission.EN_COURS);
            assertThat(statut(bloquee)).isEqualTo(StatutSoumission.EN_COURS);

            // Verrou relâché : la copie est clôturée au passage suivant
            liberer.countDown();
            detenteur.get(10, TimeUnit.SECONDS);
            clotureCopiesService.cloturerCopiesExpirees();
            assertThat(statut(bloquee)).isNotEqualTo(StatutSoumission.EN_COURS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }
}
