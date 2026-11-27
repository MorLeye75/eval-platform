package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.dto.correction.RevisionNoteRequest;
import sn.ept.eval.exception.CorrectionNonModifiableException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Révision d'une note et publication des résultats au même moment, sur un vrai PostgreSQL : la publication attend la
 * fin de la révision (verrou partagé de l'évaluation) et publie la note revue ; une révision arrivée après la
 * publication est refusée. Aucune note ne change donc jamais après la publication. Ignoré si Docker n'est pas disponible.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class RevisionNotePostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private CopieEnseignantService copieEnseignantService;
    @Autowired private PublicationResultatsService publicationResultatsService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Publication pendant une révision : elle attend la fin de la révision et publie la note revue ; une révision après publication est refusée")
    void publicationAttendLaRevision() throws Exception {
        Long[] ids = copieNotee();
        Long evaluationId = ids[0], copieId = ids[1], reponseId = ids[2];

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch revisee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        try {
            // Révision dans une transaction laissée ouverte jusqu'au signal : elle détient le verrou partagé de l'évaluation
            Future<?> revision = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                copieEnseignantService.reviser(reponseId, new RevisionNoteRequest(4.0, "Sortie juste à un espace près."), "prof@ept.sn");
                revisee.countDown();
                try {
                    liberer.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(revisee.await(20, TimeUnit.SECONDS)).isTrue();

            Future<?> publication = pool.submit(() -> publicationResultatsService.publier(evaluationId, "prof@ept.sn"));
            Thread.sleep(1500);
            assertThat(publication.isDone()).as("la publication doit attendre la fin de la révision").isFalse();

            liberer.countDown();
            revision.get(20, TimeUnit.SECONDS);
            publication.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }

        assertThat(evaluationRepository.findById(evaluationId).orElseThrow().getResultatsPublies()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select note_obtenue from reponses_etudiant where id = ?", Double.class, reponseId))
                .isEqualTo(4.0);
        assertThat(soumissionRepository.findById(copieId).orElseThrow().getNoteFinale()).isEqualTo(4.0);

        assertThatThrownBy(() -> copieEnseignantService.reviser(reponseId, new RevisionNoteRequest(3.0, "Après publication"), "prof@ept.sn"))
                .isInstanceOf(CorrectionNonModifiableException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from revisions_note where reponse_id = ?", Integer.class, reponseId))
                .isEqualTo(1);
    }

    /** Évaluation terminée depuis 1 h (marge écoulée), résultats non publiés, une copie EVALUE : code noté 2/4 par Judge0. */
    private Long[] copieNotee() {
        String email = "etu-rev-pg-" + UUID.randomUUID() + "@ept.sn";
        Promotion promotion = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Revision", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), promotion));
        return transactionTemplate.execute(tx -> {
            Evaluation e = new Evaluation();
            e.setTitre("Révision concurrente " + UUID.randomUUID());
            e.setDureeStandard(30);
            e.setDateOuverture(LocalDateTime.now().minusHours(3));
            e.setDateFermeture(LocalDateTime.now().minusHours(1));
            e.setStatut(StatutEvaluation.TERMINEE);
            e.setResultatsPublies(false);
            e.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            e.setPromotions(new HashSet<>(Set.of(promotion)));
            QuestionCode code = new QuestionCode("Afficher l'entrée", 1, 4.0);
            code.addCasDeTest(new CasDeTest("a", "a", false));
            e.addQuestion(code);
            e = evaluationRepository.saveAndFlush(e);

            Soumission s = new Soumission();
            s.setEvaluation(e);
            s.setEtudiant((Etudiant) utilisateurRepository.findByEmail(email).orElseThrow());
            s.setDateRemise(LocalDateTime.now().minusHours(1).minusMinutes(5));
            ReponseCode rc = new ReponseCode();
            rc.setQuestion(code);
            rc.setCodeSoumis("print(input())");
            rc.setLangage("python");
            rc.setNoteObtenue(2.0);
            rc.setValideeParProf(true);
            s.addReponse(rc);
            s.setStatut(StatutSoumission.EVALUE);
            s.setNoteFinale(2.0);
            s = soumissionRepository.saveAndFlush(s);
            return new Long[]{e.getId(), s.getId(), rc.getId()};
        });
    }
}
