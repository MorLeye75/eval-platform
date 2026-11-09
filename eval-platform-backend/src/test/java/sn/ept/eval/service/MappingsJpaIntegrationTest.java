package sn.ept.eval.service;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;
import sn.ept.eval.dto.soumission.SoumissionDetailResponse;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutProposition;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mappings JPA vus depuis le SQL réellement émis : mise à jour ciblée des choix cochés (Set, pas de « bag »)
 * et proposition IA non chargée avec chaque réponse ouverte (LAZY).
 * Base H2 propre à cette classe ; les requêtes SQL sont capturées par un StatementInspector.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mappings;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "sn.ept.eval.service.MappingsJpaIntegrationTest$CaptureSql"
})
class MappingsJpaIntegrationTest {

    public static class CaptureSql implements StatementInspector {
        static final List<String> REQUETES = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            REQUETES.add(sql.toLowerCase(Locale.ROOT));
            return sql;
        }
    }

    @Autowired
    private SoumissionService soumissionService;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private ReponseEtudiantRepository reponseEtudiantRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SoumissionMapper soumissionMapper;

    @Autowired
    private SoumissionRepository soumissionRepository;

    private String email;
    private Long soumissionId;
    private Long qcmId;
    private Long ouverteId;
    private List<Long> choixIds;

    /** Évaluation ouverte : un QCM à choix multiples (3 choix) et une question ouverte. */
    @BeforeEach
    void setUp() {
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        Long evaluationId = transactionTemplate.execute(tx -> {
            Evaluation evaluation = new Evaluation();
            evaluation.setTitre("Mappings " + UUID.randomUUID());
            evaluation.setDureeStandard(30);
            evaluation.setDateOuverture(LocalDateTime.now().minusHours(1));
            evaluation.setDateFermeture(LocalDateTime.now().plusHours(2));
            evaluation.setStatut(StatutEvaluation.EN_COURS);
            evaluation.setEnseignant((Enseignant) utilisateurRepository.findByEmail("prof@ept.sn").orElseThrow());
            evaluation.getPromotions().add(dic2);
            QuestionQCM qcm = new QuestionQCM("Structures linéaires ?", 1, 2.0, true);
            qcm.addChoix(new ChoixReponse("Pile", true));
            qcm.addChoix(new ChoixReponse("File", true));
            qcm.addChoix(new ChoixReponse("Arbre", false));
            evaluation.addQuestion(qcm);
            evaluation.addQuestion(new QuestionOuverte("Expliquez la récursivité.", 2, 3.0));
            return evaluationRepository.save(evaluation).getId();
        });
        transactionTemplate.executeWithoutResult(tx -> {
            List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(evaluationId);
            qcmId = questions.get(0).getId();
            ouverteId = questions.get(1).getId();
            choixIds = ((QuestionQCM) questions.get(0)).getChoix().stream().map(ChoixReponse::getId).sorted().toList();
        });

        String suffixe = UUID.randomUUID().toString();
        email = "etu-" + suffixe + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Etudiant", email, "x", "ETU-" + suffixe, dic2));
        soumissionId = soumissionService.demarrerOuReprendre(evaluationId, email).getId();
    }

    private void sauvegarder(ReponseItemDto item) {
        soumissionService.sauvegarderReponses(soumissionId, new SauvegardeReponsesRequest(List.of(item)), email);
    }

    @Test
    @DisplayName("Changer un choix coché ne supprime et n'insère que ce choix, pas tous les choix de la réponse")
    void choixCoches_MiseAJourCiblee() {
        Long a = choixIds.get(0), b = choixIds.get(1), c = choixIds.get(2);
        sauvegarder(new ReponseItemDto(qcmId, null, null, List.of(a, b)));

        CaptureSql.REQUETES.clear();
        sauvegarder(new ReponseItemDto(qcmId, null, null, List.of(a, c)));

        List<String> surLesChoix = CaptureSql.REQUETES.stream().filter(sql -> sql.contains("reponse_qcm_choix")).toList();
        List<String> suppressions = surLesChoix.stream().filter(sql -> sql.startsWith("delete")).toList();
        assertThat(suppressions).isNotEmpty().allMatch(sql -> sql.contains("choix_id"),
                "suppression ciblée sur le choix retiré (pas de « delete ... where reponse_qcm_id=? » de toute la collection)");
        assertThat(surLesChoix.stream().filter(sql -> sql.startsWith("insert"))).hasSize(1);

        SoumissionDetailResponse copie = soumissionService.getDetail(soumissionId, email);
        assertThat(copie.getQuestions().get(0).getReponseActuelle().getChoixIds()).containsExactly(a, c);
    }

    @Test
    @DisplayName("Charger une copie ne charge pas les propositions IA des réponses ouvertes")
    void propositionIA_PasChargeeAvecLaReponse() {
        sauvegarder(new ReponseItemDto(ouverteId, "Une fonction qui s'appelle elle-même.", null, null));
        transactionTemplate.executeWithoutResult(tx -> {
            ReponseOuverte reponse = reponseEtudiantRepository.findBySoumissionId(soumissionId).stream()
                    .filter(ReponseOuverte.class::isInstance).map(ReponseOuverte.class::cast).findFirst().orElseThrow();
            reponse.setPropositionIA(new PropositionIA(2.5, "Définition correcte, sans exemple.", StatutProposition.EN_ATTENTE));
        });

        CaptureSql.REQUETES.clear();
        SoumissionDetailResponse copie = soumissionService.getDetail(soumissionId, "prof@ept.sn");

        assertThat(copie.getQuestions()).hasSize(2);
        assertThat(CaptureSql.REQUETES).noneMatch(sql -> sql.contains("propositions_ia"));
    }

    @Test
    @DisplayName("Questions déjà présentes sous forme de proxy dans la transaction : la copie garde ses choix")
    void questionsProxy_ChoixConserves() {
        sauvegarder(new ReponseItemDto(qcmId, null, null, List.of(choixIds.get(0))));

        int nbChoix = transactionTemplate.execute(tx -> {
            // Charger d'abord les réponses place leurs questions (LAZY) en proxy dans la session
            reponseEtudiantRepository.findBySoumissionId(soumissionId).forEach(r -> r.getQuestion().getId());
            Soumission soumission = soumissionRepository.findById(soumissionId).orElseThrow();
            return soumissionMapper.versDetail(soumission).getQuestions().get(0).getChoixPossibles().size();
        });

        assertThat(nbChoix).isEqualTo(3);
    }
}
