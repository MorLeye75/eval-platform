package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.evaluation.CasDeTestDTO;
import sn.ept.eval.dto.evaluation.ChoixDTO;
import sn.ept.eval.dto.evaluation.CreateEvaluationRequest;
import sn.ept.eval.dto.evaluation.EvaluationResponse;
import sn.ept.eval.dto.evaluation.QuestionRequestDTO;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.QuestionOuverte;
import sn.ept.eval.model.QuestionQCM;
import sn.ept.eval.model.QuestionQuiz;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.TypeQuestion;
import sn.ept.eval.repository.AmenagementTempsRepository;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DS-03 (création, modification, suppression) et DS-04 (publication, dépublication). */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 10, 26, 10, 0);

    @Mock private EvaluationRepository evaluationRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private SoumissionRepository soumissionRepository;
    @Mock private AmenagementTempsRepository amenagementTempsRepository;
    @Mock private sn.ept.eval.repository.EtudiantRepository etudiantRepository;

    private EvaluationService service;
    private Enseignant prof;
    private Enseignant autreProf;
    private Promotion dic2;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(MAINTENANT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new EvaluationService(evaluationRepository, promotionRepository, utilisateurRepository,
                soumissionRepository, amenagementTempsRepository, etudiantRepository, clock);

        prof = new Enseignant("Sow", "Mamadou", "prof@ept.sn", "x", "ENS-1");
        prof.setId(1L);
        autreProf = new Enseignant("Fall", "Awa", "autre@ept.sn", "x", "ENS-2");
        autreProf.setId(2L);
        dic2 = new Promotion("DIC2", "2025/2026");
        dic2.setId(5L);

        lenient().when(utilisateurRepository.findByEmail("prof@ept.sn")).thenReturn(Optional.of(prof));
    }

    // ---------------------------------------------------------------- DS-03 création

    @Test
    @DisplayName("Création : brouillon, enseignant du JWT, questions numérotées par position")
    void creer_Brouillon() {
        when(promotionRepository.findAllById(any())).thenReturn(List.of(dic2));
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));

        EvaluationResponse r = service.creer(requeteValide(), "prof@ept.sn");

        ArgumentCaptor<Evaluation> captor = ArgumentCaptor.forClass(Evaluation.class);
        verify(evaluationRepository).save(captor.capture());
        Evaluation enregistree = captor.getValue();
        assertThat(enregistree.getStatut()).isEqualTo(StatutEvaluation.BROUILLON);
        assertThat(enregistree.getEnseignant()).isSameAs(prof);
        assertThat(enregistree.getPromotions()).containsExactly(dic2);
        assertThat(enregistree.getQuestions()).extracting(q -> q.getOrdre()).containsExactly(1, 2, 3);
        assertThat(enregistree.getQuestions().get(0)).isInstanceOf(QuestionQCM.class);
        assertThat(enregistree.getQuestions().get(1)).isInstanceOf(QuestionQuiz.class);
        assertThat(enregistree.getQuestions().get(2)).isInstanceOf(QuestionOuverte.class);
        assertThat(r.getNbQuestions()).isEqualTo(3);
        assertThat(r.getStatut()).isEqualTo(StatutEvaluation.BROUILLON);
    }

    @Test
    @DisplayName("Création : compte qui n'est pas un enseignant → 404, rien n'est enregistré")
    void creer_ProfilIntrouvable() {
        when(utilisateurRepository.findByEmail("inconnu@ept.sn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creer(requeteValide(), "inconnu@ept.sn"))
                .isInstanceOf(RessourceNonTrouveeException.class);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Création : dateFermeture ≤ dateOuverture → 400")
    void creer_DatesIncoherentes() {
        CreateEvaluationRequest requete = requeteValide();
        requete.setDateFermeture(requete.getDateOuverture());

        assertThatThrownBy(() -> service.creer(requete, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postérieure");
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Création : promotion inconnue → 404")
    void creer_PromotionInconnue() {
        when(promotionRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.creer(requeteValide(), "prof@ept.sn"))
                .isInstanceOf(RessourceNonTrouveeException.class)
                .hasMessageContaining("Promotion");
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("QCM : moins de 2 choix, aucun choix correct, ou 2 corrects sans choix multiple → 400")
    void creer_ReglesQcm() {
        assertQuestionRejetee(qcm(false, new ChoixDTO("A", true)), "au moins 2 choix");
        assertQuestionRejetee(qcm(false, new ChoixDTO("A", false), new ChoixDTO("B", false)), "au moins un choix doit être correct");
        assertQuestionRejetee(qcm(false, new ChoixDTO("A", true), new ChoixDTO("B", true)), "un seul choix");
    }

    @Test
    @DisplayName("QCM à choix multiples : plusieurs bonnes réponses acceptées")
    void creer_QcmMultipleAccepte() {
        when(promotionRepository.findAllById(any())).thenReturn(List.of(dic2));
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));
        CreateEvaluationRequest requete = requeteValide();
        requete.setQuestions(List.of(qcm(true, new ChoixDTO("A", true), new ChoixDTO("B", true), new ChoixDTO("C", false))));

        assertThat(service.creer(requete, "prof@ept.sn").getNbQuestions()).isEqualTo(1);
    }

    @Test
    @DisplayName("Quiz sans durée par question → 400 ; le message nomme la question fautive")
    void creer_ReglesQuiz() {
        QuestionRequestDTO quiz = qcm(false, new ChoixDTO("A", true), new ChoixDTO("B", false));
        quiz.setType(TypeQuestion.QUIZ_CHRONOMETREE);
        quiz.setDureeQuestion(0);

        CreateEvaluationRequest requete = requeteValide();
        requete.setQuestions(List.of(ouverte(), quiz));

        assertThatThrownBy(() -> service.creer(requete, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Question 2 :")
                .hasMessageContaining("durée par question");
    }

    @Test
    @DisplayName("Code sans cas de test → 400")
    void creer_ReglesCode() {
        QuestionRequestDTO code = new QuestionRequestDTO();
        code.setType(TypeQuestion.CODE);
        code.setEnonce("Écrire fib(n)");
        code.setPoints(5.0);

        assertQuestionRejetee(code, "au moins un cas de test");

        code.setCasTests(List.of(new CasDeTestDTO("5", "5", false)));
        // avec un cas de test, la question est acceptée
        when(promotionRepository.findAllById(any())).thenReturn(List.of(dic2));
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));
        CreateEvaluationRequest requete = requeteValide();
        requete.setQuestions(List.of(code));
        assertThat(service.creer(requete, "prof@ept.sn").getNbQuestions()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cas de test : entrée facultative (absente ou vide), enregistrée comme absente")
    void creer_CodeEntreeFacultative() {
        when(promotionRepository.findAllById(any())).thenReturn(List.of(dic2));
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));
        QuestionRequestDTO code = new QuestionRequestDTO();
        code.setType(TypeQuestion.CODE);
        code.setEnonce("Afficher bonjour");
        code.setPoints(3.0);
        code.setCasTests(List.of(new CasDeTestDTO(null, "Bonjour", false), new CasDeTestDTO("", "Bonjour", true),
                new CasDeTestDTO("Awa", "Bonjour Awa", false)));
        CreateEvaluationRequest requete = requeteValide();
        requete.setQuestions(List.of(code));

        EvaluationResponse r = service.creer(requete, "prof@ept.sn");

        assertThat(r.getQuestions().get(0).getCasTests()).extracting(c -> c.getEntree())
                .containsExactly(null, null, "Awa");
    }

    @Test
    @DisplayName("Cas de test : sortie attendue vide ou blanche → 400 (Judge0 la prendrait pour absente)")
    void creer_CodeSortieVide() {
        QuestionRequestDTO code = new QuestionRequestDTO();
        code.setType(TypeQuestion.CODE);
        code.setEnonce("Ne rien afficher");
        code.setPoints(3.0);

        code.setCasTests(List.of(new CasDeTestDTO("1", "", false)));
        assertQuestionRejetee(code, "sortie attendue non vide");

        code.setCasTests(List.of(new CasDeTestDTO("1", "ok", false), new CasDeTestDTO("2", "  ", false)));
        assertQuestionRejetee(code, "sortie attendue non vide");
    }

    // ---------------------------------------------------------------- DS-03 modification, suppression

    @Test
    @DisplayName("Modification : brouillon de son propriétaire → questions remplacées")
    void modifier_Brouillon() {
        Evaluation brouillon = evaluation(StatutEvaluation.BROUILLON, prof);
        brouillon.addQuestion(new QuestionOuverte("Ancienne question", 1, 2.0));
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(brouillon));
        when(promotionRepository.findAllById(any())).thenReturn(List.of(dic2));
        when(evaluationRepository.saveAndFlush(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));

        EvaluationResponse r = service.modifier(10L, requeteValide(), "prof@ept.sn");

        assertThat(r.getNbQuestions()).isEqualTo(3);
        assertThat(brouillon.getQuestions()).noneMatch(q -> q.getEnonce().equals("Ancienne question"));
    }

    @Test
    @DisplayName("Modification : évaluation publiée → 409 EvaluationNonModifiable, rien n'est modifié")
    void modifier_NonBrouillon() {
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.PUBLIEE, prof)));

        assertThatThrownBy(() -> service.modifier(10L, requeteValide(), "prof@ept.sn"))
                .isInstanceOf(EvaluationNonModifiableException.class);
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Modification d'une évaluation d'un autre enseignant → 403")
    void modifier_AutreProprietaire() {
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.BROUILLON, autreProf)));

        assertThatThrownBy(() -> service.modifier(10L, requeteValide(), "prof@ept.sn"))
                .isInstanceOf(AccesRefuseException.class);
    }

    @Test
    @DisplayName("Évaluation inconnue → 404")
    void details_Inconnue() {
        when(evaluationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(99L, "prof@ept.sn")).isInstanceOf(RessourceNonTrouveeException.class);
    }

    @Test
    @DisplayName("Suppression : brouillon supprimé, évaluation publiée refusée (409)")
    void supprimer() {
        Evaluation brouillon = evaluation(StatutEvaluation.BROUILLON, prof);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(brouillon));
        service.supprimer(10L, "prof@ept.sn");
        verify(evaluationRepository).delete(brouillon);

        when(evaluationRepository.findById(11L)).thenReturn(Optional.of(evaluation(StatutEvaluation.EN_COURS, prof)));
        assertThatThrownBy(() -> service.supprimer(11L, "prof@ept.sn")).isInstanceOf(EvaluationNonModifiableException.class);
    }

    @Test
    @DisplayName("Liste : seulement les évaluations de l'enseignant connecté, sans questions")
    void mesEvaluations() {
        Evaluation e = evaluation(StatutEvaluation.BROUILLON, prof);
        e.addQuestion(new QuestionOuverte("Q", 1, 2.0));
        e.setPromotions(Set.of(dic2));
        when(evaluationRepository.findByEnseignantIdOrderByDateOuvertureDesc(1L)).thenReturn(List.of(e));
        SoumissionRepository.NbCopies copies = org.mockito.Mockito.mock(SoumissionRepository.NbCopies.class);
        when(copies.getEvaluationId()).thenReturn(10L);
        when(copies.getNbCopies()).thenReturn(32L);
        when(soumissionRepository.compterCopiesParEvaluation(1L)).thenReturn(List.of(copies));
        when(etudiantRepository.countByPromotionId(5L)).thenReturn(45L);

        List<EvaluationResponse> liste = service.mesEvaluations("prof@ept.sn");

        assertThat(liste).hasSize(1);
        assertThat(liste.get(0).getNbQuestions()).isEqualTo(1);
        assertThat(liste.get(0).getQuestions()).isEmpty();
        assertThat(liste.get(0).getNbCopies()).isEqualTo(32);   // soumissions 32 / 45 attendus
        assertThat(liste.get(0).getNbAttendus()).isEqualTo(45);
    }

    // ---------------------------------------------------------------- DS-04 publication

    @Test
    @DisplayName("Publication : BROUILLON avec questions et fermeture future → PUBLIEE")
    void publier() {
        Evaluation brouillon = evaluation(StatutEvaluation.BROUILLON, prof);
        brouillon.addQuestion(new QuestionOuverte("Q", 1, 2.0));
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(brouillon));
        when(evaluationRepository.saveAndFlush(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.publier(10L, "prof@ept.sn").getStatut()).isEqualTo(StatutEvaluation.PUBLIEE);
    }

    @Test
    @DisplayName("Publication : déjà publiée → 409 ; sans question → 400 ; fermeture passée → 400")
    void publier_Refus() {
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.PUBLIEE, prof)));
        assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn")).isInstanceOf(EvaluationNonModifiableException.class);

        when(evaluationRepository.findById(11L)).thenReturn(Optional.of(evaluation(StatutEvaluation.BROUILLON, prof)));
        assertThatThrownBy(() -> service.publier(11L, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("au moins une question");

        Evaluation fermee = evaluation(StatutEvaluation.BROUILLON, prof);
        fermee.addQuestion(new QuestionOuverte("Q", 1, 2.0));
        fermee.setDateFermeture(MAINTENANT.minusMinutes(1));
        when(evaluationRepository.findById(12L)).thenReturn(Optional.of(fermee));
        assertThatThrownBy(() -> service.publier(12L, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fermeture");

        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Publication d'une évaluation d'un autre enseignant → 403")
    void publier_AutreProprietaire() {
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.BROUILLON, autreProf)));

        assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn")).isInstanceOf(AccesRefuseException.class);
    }

    @Test
    @DisplayName("Publication : durée standard plus longue que la plage d'ouverture → 400, reste en brouillon")
    void publier_DureeStandardPlusLongueQueLaPlage() {
        Evaluation brouillon = evaluation(StatutEvaluation.BROUILLON, prof);
        brouillon.addQuestion(new QuestionOuverte("Q", 1, 2.0));
        brouillon.setDureeStandard(120);
        brouillon.setDateFermeture(brouillon.getDateOuverture().plusMinutes(90));
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(brouillon));

        assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plage d'ouverture (90 min)")
                .hasMessageContaining("120 min");
        assertThat(brouillon.getStatut()).isEqualTo(StatutEvaluation.BROUILLON);
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Publication : un tiers-temps trop long pour la plage (dates raccourcies après coup) → 400")
    void publier_TiersTempsHorsPlage() {
        Evaluation brouillon = evaluation(StatutEvaluation.BROUILLON, prof);
        brouillon.addQuestion(new QuestionOuverte("Q", 1, 2.0));
        brouillon.setDateFermeture(brouillon.getDateOuverture().plusMinutes(70)); // plage 70 min, durée standard 60
        sn.ept.eval.model.Etudiant etudiant = new sn.ept.eval.model.Etudiant("N", "A", "e@ept.sn", "x", "ETU-1", dic2);
        etudiant.setId(7L);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(brouillon));
        when(amenagementTempsRepository.findByEvaluationId(10L))
                .thenReturn(List.of(new sn.ept.eval.model.AmenagementTemps(etudiant, brouillon, 80)));

        assertThatThrownBy(() -> service.publier(10L, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plage d'ouverture (70 min)")
                .hasMessageContaining("80 min, tiers-temps compris");
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Publication : durée maximale exactement égale à la plage → acceptée")
    void publier_DureeEgaleALaPlage() {
        Evaluation brouillon = evaluation(StatutEvaluation.BROUILLON, prof);
        brouillon.addQuestion(new QuestionOuverte("Q", 1, 2.0));
        brouillon.setDateFermeture(brouillon.getDateOuverture().plusMinutes(60));
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(brouillon));
        when(evaluationRepository.saveAndFlush(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.publier(10L, "prof@ept.sn").getStatut()).isEqualTo(StatutEvaluation.PUBLIEE);
    }

    // ---------------------------------------------------------------- DS-04 dépublication

    @Test
    @DisplayName("Dépublication : PUBLIEE sans copie → BROUILLON, avec verrou sur l'évaluation")
    void depublier() {
        Evaluation publiee = evaluation(StatutEvaluation.PUBLIEE, prof);
        when(evaluationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(publiee));
        when(soumissionRepository.existsByEvaluationId(10L)).thenReturn(false);
        when(evaluationRepository.saveAndFlush(any(Evaluation.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.depublier(10L, "prof@ept.sn").getStatut()).isEqualTo(StatutEvaluation.BROUILLON);
    }

    @Test
    @DisplayName("Dépublication refusée (409) si une copie existe ou si l'évaluation est déjà en cours")
    void depublier_Refus() {
        when(evaluationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.PUBLIEE, prof)));
        when(soumissionRepository.existsByEvaluationId(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.depublier(10L, "prof@ept.sn"))
                .isInstanceOf(EvaluationNonModifiableException.class).hasMessageContaining("déjà commencé");

        when(evaluationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(evaluation(StatutEvaluation.EN_COURS, prof)));
        assertThatThrownBy(() -> service.depublier(11L, "prof@ept.sn")).isInstanceOf(EvaluationNonModifiableException.class);

        when(evaluationRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(evaluation(StatutEvaluation.BROUILLON, prof)));
        assertThatThrownBy(() -> service.depublier(12L, "prof@ept.sn")).isInstanceOf(EvaluationNonModifiableException.class);
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Dépublication d'une évaluation d'un autre enseignant → 403, inconnue → 404")
    void depublier_Acces() {
        when(evaluationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(evaluation(StatutEvaluation.PUBLIEE, autreProf)));
        assertThatThrownBy(() -> service.depublier(10L, "prof@ept.sn")).isInstanceOf(AccesRefuseException.class);

        when(evaluationRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.depublier(99L, "prof@ept.sn")).isInstanceOf(RessourceNonTrouveeException.class);
    }

    // ---------------------------------------------------------------- outils

    private void assertQuestionRejetee(QuestionRequestDTO question, String messageAttendu) {
        CreateEvaluationRequest requete = requeteValide();
        requete.setQuestions(List.of(question));
        assertThatThrownBy(() -> service.creer(requete, "prof@ept.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(messageAttendu);
        verify(evaluationRepository, never()).save(any());
    }

    private CreateEvaluationRequest requeteValide() {
        CreateEvaluationRequest r = new CreateEvaluationRequest();
        r.setTitre("Réseaux — QCM");
        r.setDescription("Contrôle");
        r.setDureeStandard(60);
        r.setDateOuverture(MAINTENANT.plusDays(1));
        r.setDateFermeture(MAINTENANT.plusDays(2));
        r.setPromotionIds(List.of(5L));

        QuestionRequestDTO quiz = qcm(false, new ChoixDTO("22", true), new ChoixDTO("80", false));
        quiz.setType(TypeQuestion.QUIZ_CHRONOMETREE);
        quiz.setDureeQuestion(20);
        r.setQuestions(List.of(
                qcm(false, new ChoixDTO("TCP", true), new ChoixDTO("UDP", false)),
                quiz,
                ouverte()));
        return r;
    }

    private QuestionRequestDTO qcm(boolean multiple, ChoixDTO... choix) {
        QuestionRequestDTO q = new QuestionRequestDTO();
        q.setType(TypeQuestion.QCM);
        q.setEnonce("Question ?");
        q.setPoints(2.0);
        q.setEstChoixMultiple(multiple);
        q.setChoix(List.of(choix));
        return q;
    }

    private QuestionRequestDTO ouverte() {
        QuestionRequestDTO q = new QuestionRequestDTO();
        q.setType(TypeQuestion.OUVERTE);
        q.setEnonce("Expliquez TCP");
        q.setPoints(4.0);
        return q;
    }

    private Evaluation evaluation(StatutEvaluation statut, Enseignant proprietaire) {
        Evaluation e = new Evaluation();
        e.setId(10L);
        e.setTitre("Évaluation");
        e.setDureeStandard(60);
        e.setDateOuverture(MAINTENANT.plusDays(1));
        e.setDateFermeture(MAINTENANT.plusDays(2));
        e.setStatut(statut);
        e.setEnseignant(proprietaire);
        return e;
    }
}
