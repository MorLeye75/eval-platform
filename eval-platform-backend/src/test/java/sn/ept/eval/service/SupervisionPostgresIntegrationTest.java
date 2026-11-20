package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.dto.evaluation.SupervisionResponse;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.AlerteAntiTriche;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.Question;
import sn.ept.eval.model.ReponseOuverte;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.AlerteAntiTricheRepository;
import sn.ept.eval.repository.SoumissionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import sn.ept.eval.model.QuestionOuverte;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les requêtes de comptage de la supervision (DS-17 : trim, size sur une collection) s'exécutent sur un vrai
 * PostgreSQL, que H2 ne valide pas fidèlement. Ignoré sans Docker.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class SupervisionPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private SupervisionService supervisionService;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private SoumissionService soumissionService;
    @Autowired private SoumissionRepository soumissionRepository;
    @Autowired private AlerteAntiTricheRepository alerteRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private NotificationService notificationService;

    @Test
    @DisplayName("La supervision s'exécute sur PostgreSQL : étudiants attendus listés, aucun commencé, aucune alerte")
    void supervisionSurPostgres() {
        String email = "prof-" + UUID.randomUUID() + "@ept.sn";
        Long evaluationId = transactionTemplate.execute(tx -> {
            Promotion promotion = promotionRepository.save(new Promotion("DIC-" + UUID.randomUUID(), "2026"));
            Enseignant prof = utilisateurRepository.save(new Enseignant("Sow", "Mamadou", email, "x", "ENS-" + UUID.randomUUID()));
            utilisateurRepository.save(new Etudiant("Diop", "Moussa", "e-" + UUID.randomUUID() + "@ept.sn", "x", "N-" + UUID.randomUUID(), promotion));
            Evaluation e = new Evaluation();
            e.setTitre("Supervision PG");
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusMinutes(30));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotion)));
            e.addQuestion(new QuestionOuverte("Question", 1, 5.0));
            return evaluationRepository.save(e).getId();
        });

        SupervisionResponse reponse = supervisionService.supervision(evaluationId, email);

        assertThat(reponse.nbAttendus()).isEqualTo(1);
        assertThat(reponse.nbCommences()).isZero();
        assertThat(reponse.nbAlertes()).isZero();
        assertThat(reponse.etudiants().get(0).etat()).isEqualTo(SupervisionResponse.EtatCopie.NON_COMMENCE);
    }

    @Test
    @DisplayName("Avec des données sur PostgreSQL : états, réponses réellement données (une vide ne compte pas), onglets, alertes triées, totaux")
    void supervisionAvecDonneesSurPostgres() {
        String email = "prof-" + UUID.randomUUID() + "@ept.sn";
        String emailA = "alpha-" + UUID.randomUUID() + "@ept.sn";
        String emailB = "bravo-" + UUID.randomUUID() + "@ept.sn";
        Long evaluationId = transactionTemplate.execute(tx -> {
            Promotion promotion = promotionRepository.save(new Promotion("DIC-" + UUID.randomUUID(), "2026"));
            Enseignant prof = utilisateurRepository.save(new Enseignant("Sow", "Mamadou", email, "x", "ENS-" + UUID.randomUUID()));
            utilisateurRepository.save(new Etudiant("Alpha", "Awa", emailA, "x", "N-" + UUID.randomUUID(), promotion));
            utilisateurRepository.save(new Etudiant("Bravo", "Omar", emailB, "x", "N-" + UUID.randomUUID(), promotion));
            utilisateurRepository.save(new Etudiant("Charlie", "Mame", "c-" + UUID.randomUUID() + "@ept.sn", "x", "N-" + UUID.randomUUID(), promotion));
            Evaluation e = new Evaluation();
            e.setTitre("Supervision PG avec données");
            e.setDureeStandard(60);
            e.setDateOuverture(LocalDateTime.now().minusMinutes(30));
            e.setDateFermeture(LocalDateTime.now().plusHours(2));
            e.setStatut(StatutEvaluation.EN_COURS);
            e.setResultatsPublies(false);
            e.setEnseignant(prof);
            e.setPromotions(new HashSet<>(Set.of(promotion)));
            e.addQuestion(new QuestionOuverte("Q1", 1, 5.0));
            e.addQuestion(new QuestionOuverte("Q2", 2, 5.0));
            e.addQuestion(new QuestionOuverte("Q3", 3, 5.0));
            return evaluationRepository.save(e).getId();
        });
        Long copieA = copie(evaluationId, emailA, StatutSoumission.EN_COURS, "oui", "non", "   "); // 2 réponses données sur 3
        copie(evaluationId, emailB, StatutSoumission.SOUMIS, "a", "b", "c");                          // 3 sur 3, remise
        jdbcTemplate.update("update soumissions set nb_changements_onglet = 4 where id = ?", copieA);
        alerte(copieA, "Sorties d'écran répétées (3)", NiveauAlerte.MODERE, LocalDateTime.now().minusMinutes(10));
        alerte(copieA, "Sorties d'écran répétées (5)", NiveauAlerte.ELEVE, LocalDateTime.now().minusMinutes(2));

        SupervisionResponse reponse = supervisionService.supervision(evaluationId, email);

        assertThat(reponse.nbAttendus()).isEqualTo(3);
        assertThat(reponse.nbCommences()).isEqualTo(2);
        assertThat(reponse.nbSoumis()).isEqualTo(1);
        assertThat(reponse.nbAlertes()).isEqualTo(2);
        assertThat(reponse.alertes().get(0).niveau()).isEqualTo(NiveauAlerte.ELEVE); // la plus récente d'abord
        SupervisionResponse.EtudiantSupervise alpha = reponse.etudiants().stream().filter(x -> x.nom().equals("Alpha")).findFirst().orElseThrow();
        assertThat(alpha.etat()).isEqualTo(SupervisionResponse.EtatCopie.EN_COURS);
        assertThat(alpha.nbReponses()).isEqualTo(2);
        assertThat(alpha.nbChangementsOnglet()).isEqualTo(4);
        assertThat(alpha.nbAlertes()).isEqualTo(2);
        assertThat(reponse.etudiants().stream().filter(x -> x.nom().equals("Bravo")).findFirst().orElseThrow().nbReponses()).isEqualTo(3);
        assertThat(reponse.etudiants().stream().filter(x -> x.nom().equals("Charlie")).findFirst().orElseThrow().etat())
                .isEqualTo(SupervisionResponse.EtatCopie.NON_COMMENCE);
    }

    private Long copie(Long evaluationId, String email, StatutSoumission statut, String... textes) {
        Long copieId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
        transactionTemplate.executeWithoutResult(tx -> {
            Soumission copie = soumissionRepository.findById(copieId).orElseThrow();
            List<Question> questions = copie.getEvaluation().getQuestions().stream().sorted(Comparator.comparing(Question::getOrdre)).toList();
            for (int i = 0; i < questions.size(); i++) {
                ReponseOuverte r = new ReponseOuverte();
                r.setQuestion(questions.get(i));
                r.setTexteSoumis(textes[i]);
                copie.addReponse(r);
            }
            copie.setStatut(statut);
            soumissionRepository.saveAndFlush(copie);
        });
        return copieId;
    }

    private void alerte(Long copieId, String description, NiveauAlerte niveau, LocalDateTime date) {
        transactionTemplate.executeWithoutResult(tx -> {
            AlerteAntiTriche alerte = new AlerteAntiTriche(description, niveau, soumissionRepository.findById(copieId).orElseThrow());
            alerte.setDateDetection(date);
            alerteRepository.save(alerte);
        });
    }
}
