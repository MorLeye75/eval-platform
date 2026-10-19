package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.dto.soumission.SoumissionDetailResponse;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.TempsEcouleException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration sur H2 (données de DataInitializer) : vérifient le comportement transactionnel
 * réel (rollback, proxies Hibernate, correction après commit) que les tests Mockito ne couvrent pas.
 */
@SpringBootTest
class SoumissionIntegrationTest {

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private SoumissionRepository soumissionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private AlerteAntiTricheRepository alerteAntiTricheRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String emailEtudiant;
    private Long evalQcmId;
    private Long evalCodeId;

    @BeforeEach
    void setUp() {
        // Un étudiant neuf par test : une seule soumission par (évaluation, étudiant)
        String suffixe = UUID.randomUUID().toString();
        emailEtudiant = "etu-" + suffixe + "@ept.sn";
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Etudiant", emailEtudiant, "x", "ETU-" + suffixe, dic2));

        List<Evaluation> evaluations = evaluationRepository.findAll();
        evalQcmId = evaluations.stream().filter(e -> e.getTitre().contains("QCM")).findFirst().orElseThrow().getId();
        evalCodeId = evaluations.stream().filter(e -> e.getTitre().contains("code")).findFirst().orElseThrow().getId();
    }

    @Test
    @DisplayName("DS-01 (Cas critique) : le verrouillage et l'alerte sont bien persistés malgré l'exception")
    void soumissionTardive_EstPersisteeMalgreException() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        transactionTemplate.executeWithoutResult(tx -> {
            Soumission s = soumissionRepository.findById(soumissionId).orElseThrow();
            s.setDateDebut(LocalDateTime.now().minusMinutes(60));
        });

        assertThatThrownBy(() -> soumissionService.soumettreCopie(soumissionId, emailEtudiant))
                .isInstanceOf(TempsEcouleException.class);

        assertThat(soumissionRepository.findById(soumissionId).orElseThrow().getStatut())
                .isEqualTo(StatutSoumission.SOUMIS);
        assertThat(alerteAntiTricheRepository.findBySoumissionId(soumissionId)).hasSize(1);
    }

    @Test
    @DisplayName("DS-01 (Cas normal) : la correction automatique QCM note la copie après soumission")
    void soumission_QcmCorrigeAutomatiquement() throws InterruptedException {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(reponsesCorrectes()), emailEtudiant);

        soumissionService.soumettreCopie(soumissionId, emailEtudiant);

        // Correction asynchrone : on attend au plus 5 s
        StatutSoumission statut = null;
        for (int i = 0; i < 50 && statut != StatutSoumission.EVALUE; i++) {
            Thread.sleep(100);
            statut = soumissionRepository.findById(soumissionId).orElseThrow().getStatut();
        }
        assertThat(statut).isEqualTo(StatutSoumission.EVALUE);

        Double note = transactionTemplate.execute(tx ->
                soumissionRepository.findById(soumissionId).orElseThrow().getNoteFinale());
        assertThat(note).isEqualTo(5.0);
    }

    @Test
    @DisplayName("DS-02 : une réponse à une question d'une autre évaluation est rejetée")
    void sauvegarde_QuestionAutreEvaluation_Rejetee() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        Long questionCodeId = questionRepository.findByEvaluationIdOrderByOrdreAsc(evalCodeId).get(0).getId();

        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(questionCodeId, null, "return true;", null)));

        assertThatThrownBy(() -> soumissionService.sauvegarderReponses(soumissionId, request, emailEtudiant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DS-02 : un choix appartenant à une autre question est rejeté")
    void sauvegarde_ChoixAutreQuestion_Rejete() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();
        List<Long> choixQ2 = choixIds(1);
        Long q1Id = questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQcmId).get(0).getId();

        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(q1Id, null, null, List.of(choixQ2.get(0)))));

        assertThatThrownBy(() -> soumissionService.sauvegarderReponses(soumissionId, request, emailEtudiant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Consultation : l'enseignant de l'évaluation peut voir la copie, un autre étudiant non")
    void getDetail_ControleParRole() {
        Long soumissionId = soumissionService.demarrerOuReprendre(evalQcmId, emailEtudiant).getId();

        SoumissionDetailResponse detail = soumissionService.getDetail(soumissionId, "prof@ept.sn");
        assertThat(detail.getId()).isEqualTo(soumissionId);

        assertThatThrownBy(() -> soumissionService.getDetail(soumissionId, "etudiant@ept.sn"))
                .isInstanceOf(AccesRefuseException.class);
    }

    /** Bonnes réponses de l'évaluation QCM initialisée par DataInitializer. */
    private List<ReponseItemDto> reponsesCorrectes() {
        return transactionTemplate.execute(tx ->
                questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQcmId).stream()
                        .map(q -> new ReponseItemDto(q.getId(), null, null,
                                ((QuestionQCM) q).getChoix().stream()
                                        .filter(ChoixReponse::getEstCorrect)
                                        .map(ChoixReponse::getId)
                                        .toList()))
                        .toList());
    }

    private List<Long> choixIds(int indexQuestion) {
        return transactionTemplate.execute(tx -> {
            QuestionQCM q = (QuestionQCM) questionRepository.findByEvaluationIdOrderByOrdreAsc(evalQcmId).get(indexQuestion);
            return q.getChoix().stream().map(ChoixReponse::getId).toList();
        });
    }
}
