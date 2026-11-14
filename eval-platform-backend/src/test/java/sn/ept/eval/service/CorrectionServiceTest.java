package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import sn.ept.eval.config.Judge0Proprietes;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.service.judge0.Judge0Client;
import sn.ept.eval.service.judge0.Judge0Client.CasAExecuter;
import sn.ept.eval.service.judge0.Judge0Client.ResultatCas;
import sn.ept.eval.service.judge0.Judge0IndisponibleException;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Correction automatique (DS-01, DS-12) : QCM « exact ou zéro », code noté au prorata des cas réussis,
 * prise en charge par jeton, tentatives espacées puis repli en correction manuelle.
 * Base et transactions simulées : les erreurs après prise en charge (annulation réelle) sont couvertes
 * par CorrectionIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class CorrectionServiceTest {

    private static final LocalDateTime DEBUT = LocalDateTime.of(2026, 11, 8, 10, 0);

    /** Horloge que le test fait avancer entre deux tentatives. */
    static class Horloge extends Clock {
        private Instant instant = DEBUT.toInstant(ZoneOffset.UTC);

        void avancer(Duration duree) { instant = instant.plus(duree); }
        LocalDateTime maintenant() { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @Mock
    private SoumissionRepository soumissionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private Judge0Client judge0;

    private final Horloge horloge = new Horloge();
    private final CorrectionsEnFile enFile = new CorrectionsEnFile();
    private CorrectionService service;
    private Evaluation evaluation;
    private Soumission soumission;
    private QuestionQCM simple;   // 2 points, une bonne réponse (A)
    private QuestionQCM multiple; // 3 points, deux bonnes réponses (C, D)
    private ChoixReponse a, b, c, d, e;

    private static ChoixReponse choix(long id, boolean correct) {
        ChoixReponse ch = new ChoixReponse("choix " + id, correct);
        ch.setId(id);
        return ch;
    }

    private ReponseQCM reponse(QuestionQCM question, ChoixReponse... coches) {
        ReponseQCM r = new ReponseQCM();
        r.setQuestion(question);
        r.setSoumission(soumission);
        r.setChoixCoches(Set.of(coches));
        soumission.getReponses().add(r);
        return r;
    }

    /** Question de code de 5 points avec {@code nbCas} cas de test (ids 101, 102…). */
    private QuestionCode questionCode(long id, int nbCas) {
        QuestionCode q = new QuestionCode("Palindrome", 3, 5.0);
        q.setId(id);
        IntStream.rangeClosed(1, nbCas).forEach(i -> {
            CasDeTest cas = new CasDeTest("entree " + i, "sortie " + i, i % 2 == 0);
            cas.setId(100L + i);
            q.addCasDeTest(cas);
        });
        evaluation.addQuestion(q);
        return q;
    }

    private ReponseCode reponseCode(QuestionCode question, String code, String langage) {
        ReponseCode r = new ReponseCode();
        r.setId(500L + question.getId());
        r.setQuestion(question);
        r.setSoumission(soumission);
        r.setCodeSoumis(code);
        r.setLangage(langage);
        soumission.getReponses().add(r);
        return r;
    }

    private static List<ResultatCas> resultats(int reussis, int total) {
        return IntStream.rangeClosed(1, total)
                .mapToObj(i -> new ResultatCas(100L + i, i <= reussis ? VerdictCas.REUSSI : VerdictCas.MAUVAISE_REPONSE,
                        "sortie", 0.01, i <= reussis ? null : "Wrong Answer"))
                .toList();
    }

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Judge0Proprietes proprietes = new Judge0Proprietes("http://judge0", "", 2, 2, 5, 256000, 60, 1024,
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(10), null);
        service = new CorrectionService(soumissionRepository, judge0, enFile, horloge, proprietes, transactionManager,
                new sn.ept.eval.config.NotificationParDefautConfig.NotificationServiceParDefaut());

        evaluation = new Evaluation();
        evaluation.setId(10L);
        simple = new QuestionQCM("Structure LIFO ?", 1, 2.0, false);
        simple.setId(1L);
        a = choix(1, true);
        b = choix(2, false);
        simple.addChoix(a);
        simple.addChoix(b);
        multiple = new QuestionQCM("Structures linéaires ?", 2, 3.0, true);
        multiple.setId(2L);
        c = choix(3, true);
        d = choix(4, true);
        e = choix(5, false);
        multiple.addChoix(c);
        multiple.addChoix(d);
        multiple.addChoix(e);
        evaluation.addQuestion(simple);
        evaluation.addQuestion(multiple);

        soumission = new Soumission();
        soumission.setId(100L);
        soumission.setEvaluation(evaluation);
        soumission.setStatut(StatutSoumission.SOUMIS);
        lenient().when(soumissionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(soumission));
    }

    private void corriger() throws Exception {
        service.corrigerSoumissionAsync(100L).get();
    }

    // --- QCM ---

    @Test
    @DisplayName("QCM uniquement : chaque question exacte rapporte ses points, la copie est notée et passe EVALUE")
    void toutQcm_NoteEtEvalue() throws Exception {
        ReponseQCM r1 = reponse(simple, a);
        ReponseQCM r2 = reponse(multiple, c, d);

        corriger();

        assertThat(r1.getNoteObtenue()).isEqualTo(2.0);
        assertThat(r2.getNoteObtenue()).isEqualTo(3.0);
        assertThat(soumission.getNoteFinale()).isEqualTo(5.0);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EVALUE);
        assertThat(r1.getValideeParProf()).isTrue();
        assertThat(soumission.getCorrectionDemarreeLe()).isNull(); // prise en charge libérée
        verifyNoInteractions(judge0);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    @DisplayName("Choix multiples : une réponse incomplète ou avec un choix faux vaut zéro (pas de points partiels)")
    void choixMultiples_ExactOuZero() throws Exception {
        ReponseQCM incomplete = reponse(simple, a, b); // A et B : B est faux
        ReponseQCM partielle = reponse(multiple, c);   // C sans D

        corriger();

        assertThat(incomplete.getNoteObtenue()).isZero();
        assertThat(partielle.getNoteObtenue()).isZero();
        assertThat(soumission.getNoteFinale()).isZero();
    }

    @Test
    @DisplayName("Question sans réponse : aucune note pour elle, les autres comptent")
    void questionSansReponse() throws Exception {
        reponse(simple, a);

        corriger();

        assertThat(soumission.getNoteFinale()).isEqualTo(2.0);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EVALUE);
    }

    @Test
    @DisplayName("Évaluation avec une question ouverte : QCM notés, mais copie EN_CORRECTION sans note finale")
    void evaluationMixte_EnCorrection() throws Exception {
        QuestionOuverte ouverte = new QuestionOuverte("Expliquez.", 3, 5.0);
        ouverte.setId(3L);
        evaluation.addQuestion(ouverte);
        ReponseQCM r1 = reponse(simple, a);

        corriger();

        assertThat(r1.getNoteObtenue()).isEqualTo(2.0);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_CORRECTION);
        assertThat(soumission.getNoteFinale()).isNull();
    }

    // --- Prise en charge et idempotence ---

    @Test
    @DisplayName("Copie déjà corrigée : rien n'est recalculé, Judge0 n'est jamais appelé (note de l'enseignant conservée)")
    void dejaCorrigee_Ignoree() throws Exception {
        questionCode(7L, 2);
        soumission.setStatut(StatutSoumission.EVALUE);
        soumission.setNoteFinale(4.5);
        reponse(simple, b);

        corriger();

        assertThat(soumission.getNoteFinale()).isEqualTo(4.5);
        verify(soumissionRepository, never()).save(any());
        verifyNoInteractions(judge0);
    }

    @Test
    @DisplayName("Correction déjà en cours ailleurs (prise en charge il y a 5 min) : sans effet, pas de double exécution")
    void priseEnChargeEnCours_SansEffet() throws Exception {
        QuestionCode q = questionCode(7L, 2);
        reponseCode(q, "print(1)", "python");
        LocalDateTime autre = horloge.maintenant().minusMinutes(5);
        soumission.setCorrectionDemarreeLe(autre);

        corriger();

        assertThat(soumission.getCorrectionDemarreeLe()).isEqualTo(autre);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        verifyNoInteractions(judge0);
    }

    @Test
    @DisplayName("Prise en charge abandonnée depuis plus de 15 min (serveur arrêté) : la copie est reprise et corrigée")
    void priseEnChargeAbandonnee_Reprise() throws Exception {
        soumission.setCorrectionDemarreeLe(horloge.maintenant().minusMinutes(20));
        reponse(simple, a);

        corriger();

        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EVALUE);
    }

    @Test
    @DisplayName("Jeton perdu : une autre correction a repris la copie pendant les exécutions, rien n'est écrit")
    void jetonPerdu_RienNEstEcrit() throws Exception {
        QuestionCode q = questionCode(7L, 2);
        ReponseCode rc = reponseCode(q, "print(1)", "python");
        ReponseQCM rq = reponse(simple, a);
        LocalDateTime autreJeton = horloge.maintenant().plusMinutes(16);
        when(judge0.langageConnu("python")).thenReturn(true);
        when(judge0.executer(eq("print(1)"), eq("python"), anyList(), any())).thenAnswer(inv -> {
            soumission.setCorrectionDemarreeLe(autreJeton); // reprise par une autre correction
            return resultats(2, 2);
        });

        corriger();

        assertThat(soumission.getCorrectionDemarreeLe()).isEqualTo(autreJeton);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(rc.getNoteObtenue()).isNull();
        assertThat(rc.getResultatsCasTests()).isEmpty();
        assertThat(rq.getNoteObtenue()).isNull();
        assertThat(soumission.getTentativesCorrection()).isZero();
    }

    @Test
    @DisplayName("Copie modifiée pendant les exécutions (rouverte, corrigée à la main) : résultats abandonnés")
    void statutChangePendantExecutions_ResultatsAbandonnes() throws Exception {
        QuestionCode q = questionCode(7L, 2);
        ReponseCode rc = reponseCode(q, "print(1)", "python");
        when(judge0.langageConnu("python")).thenReturn(true);
        when(judge0.executer(any(), any(), anyList(), any())).thenAnswer(inv -> {
            soumission.setStatut(StatutSoumission.EN_CORRECTION);
            return resultats(2, 2);
        });

        corriger();

        assertThat(rc.getNoteObtenue()).isNull();
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_CORRECTION);
    }

    // --- Code ---

    @Test
    @DisplayName("Code : note au prorata des cas réussis, arrondie à 2 décimales (3/7 sur 5 pts = 2,14), cas masqués compris")
    void code_ProrataArrondi() throws Exception {
        QuestionCode q = questionCode(7L, 7);
        ReponseCode rc = reponseCode(q, "print(input()[::-1])", "python");
        reponse(simple, a);
        when(judge0.langageConnu("python")).thenReturn(true);
        when(judge0.executer(eq("print(input()[::-1])"), eq("python"), anyList(), any())).thenReturn(resultats(3, 7));

        corriger();

        verify(judge0).executer(any(), any(), argThat((List<CasAExecuter> cas) -> cas.size() == 7
                && cas.get(0).casId() == 101L && cas.get(1).sortieAttendue().equals("sortie 2")), // masqué compris
                eq(DEBUT.toInstant(ZoneOffset.UTC).plus(Duration.ofMinutes(10))));
        assertThat(rc.getNoteObtenue()).isEqualTo(2.14);
        assertThat(rc.getResultatsCasTests()).hasSize(7);
        assertThat(rc.getResultatsCasTests()).filteredOn(ResultatCasTest::getEstValide).hasSize(3);
        assertThat(rc.getResultatsCasTests().get(5).getMessageErreur()).isEqualTo("Wrong Answer");
        assertThat(rc.getResultatsCasTests().get(5).getVerdict()).isEqualTo(VerdictCas.MAUVAISE_REPONSE);
        assertThat(rc.getResultatsCasTests().get(0).getVerdict()).isEqualTo(VerdictCas.REUSSI);
        assertThat(rc.getResultatsCasTests().get(0).getCasDeTest().getId()).isEqualTo(101L);
        assertThat(rc.getCommentaireProf()).contains("3/7");
        // QCM et code tous notés automatiquement : note finale 2 + 2,14
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EVALUE);
        assertThat(soumission.getNoteFinale()).isEqualTo(4.14);
    }

    @Test
    @DisplayName("Code vide : 0 sans appel à Judge0, et les résultats d'une correction précédente sont supprimés")
    void codeVide_ZeroEtAnciensResultatsSupprimes() throws Exception {
        QuestionCode q = questionCode(7L, 2);
        ReponseCode rc = reponseCode(q, "   ", "python");
        ResultatCasTest ancien = new ResultatCasTest();
        ancien.setEstValide(true);
        rc.addResultat(ancien);

        corriger();

        assertThat(rc.getNoteObtenue()).isZero();
        assertThat(rc.getResultatsCasTests()).isEmpty();
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EVALUE);
        verifyNoInteractions(judge0);
    }

    @Test
    @DisplayName("Question de code sans réponse : 0, la copie peut quand même être EVALUE")
    void codeSansReponse_Zero() throws Exception {
        questionCode(7L, 2);
        reponse(simple, a);

        corriger();

        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EVALUE);
        assertThat(soumission.getNoteFinale()).isEqualTo(2.0);
        verifyNoInteractions(judge0);
    }

    @Test
    @DisplayName("Question de code sans cas de test : pas de note automatique, copie EN_CORRECTION")
    void codeSansCasDeTest_EnCorrection() throws Exception {
        QuestionCode q = questionCode(7L, 0);
        ReponseCode rc = reponseCode(q, "print(1)", "python");

        corriger();

        assertThat(rc.getNoteObtenue()).isNull();
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_CORRECTION);
        verify(judge0, never()).executer(any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Langage absent (réponse antérieure à V6) ou inconnu de Judge0 : correction manuelle")
    void langageInconnu_EnCorrection() throws Exception {
        QuestionCode q = questionCode(7L, 2);
        ReponseCode rc = reponseCode(q, "print(1)", null);

        corriger();

        assertThat(rc.getNoteObtenue()).isNull();
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_CORRECTION);
        verify(judge0, never()).executer(any(), any(), anyList(), any());
    }

    // --- Tentatives ---

    @Test
    @DisplayName("Judge0 indisponible : nouvel essai 1, 2, 4 puis 8 min plus tard, puis correction manuelle à la 5e (QCM notés)")
    void judge0Indisponible_TentativesPuisRepli() throws Exception {
        QuestionCode q = questionCode(7L, 2);
        ReponseCode rc = reponseCode(q, "print(1)", "python");
        ReponseQCM rq = reponse(simple, a);
        when(judge0.langageConnu("python")).thenReturn(true);
        when(judge0.executer(any(), any(), anyList(), any())).thenThrow(new Judge0IndisponibleException("panne"));

        int[] delais = {1, 2, 4, 8};
        for (int i = 0; i < delais.length; i++) {
            corriger();

            assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
            assertThat(soumission.getTentativesCorrection()).isEqualTo(i + 1);
            assertThat(soumission.getCorrectionDemarreeLe()).isNull();
            assertThat(soumission.getProchaineTentativeLe()).isEqualTo(horloge.maintenant().plusMinutes(delais[i]));
            assertThat(rq.getNoteObtenue()).isNull(); // rien n'est noté tant qu'une tentative reste
            horloge.avancer(Duration.ofMinutes(delais[i]));
        }

        corriger(); // 5e tentative

        assertThat(soumission.getTentativesCorrection()).isEqualTo(5);
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_CORRECTION);
        assertThat(rq.getNoteObtenue()).isEqualTo(2.0);
        assertThat(rc.getNoteObtenue()).isNull();
        assertThat(soumission.getNoteFinale()).isNull();
        verify(judge0, times(5)).executer(any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Erreur imprévue avant la prise en charge : la tentative compte (pas de reprise à la minute suivante)")
    void erreurAvantPriseEnCharge_Comptee() throws Exception {
        reponse(simple, a);
        when(soumissionRepository.findByIdForUpdate(100L))
                .thenThrow(new IllegalStateException("base indisponible"))
                .thenReturn(Optional.of(soumission));

        corriger(); // l'erreur est enregistrée, pas propagée

        verify(transactionManager).rollback(any());
        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.SOUMIS);
        assertThat(soumission.getTentativesCorrection()).isEqualTo(1);
        assertThat(soumission.getProchaineTentativeLe()).isEqualTo(horloge.maintenant().plusMinutes(1));
    }

    @Test
    @DisplayName("5e erreur imprévue : copie EN_CORRECTION, QCM sans note (la transaction a été annulée)")
    void cinquiemeErreur_EnCorrection() throws Exception {
        ReponseQCM rq = reponse(simple, a);
        soumission.setTentativesCorrection(4);
        when(soumissionRepository.findByIdForUpdate(100L))
                .thenThrow(new IllegalStateException("bug"))
                .thenReturn(Optional.of(soumission));

        corriger();

        assertThat(soumission.getStatut()).isEqualTo(StatutSoumission.EN_CORRECTION);
        assertThat(soumission.getTentativesCorrection()).isEqualTo(5);
        assertThat(rq.getNoteObtenue()).isNull();
    }

    @Test
    @DisplayName("Fin de la correction : la copie est retirée des copies en file, même après une erreur")
    void finDeCorrection_RetireeDeLaFile() throws Exception {
        enFile.ajouter(100L);
        when(soumissionRepository.findByIdForUpdate(100L)).thenThrow(new IllegalStateException("panne"));

        service.corrigerSoumissionAvecCodeAsync(100L).get();

        assertThat(enFile.contient(100L)).isFalse();
    }

    @Test
    @DisplayName("Délais entre tentatives et arrondi des notes")
    void delaisEtArrondi() {
        assertThat(IntStream.rangeClosed(1, 4).mapToObj(CorrectionService::delaiAvantTentative).map(Duration::toMinutes))
                .containsExactly(1L, 2L, 4L, 8L);
        assertThat(CorrectionService.arrondi(5.0 * 3 / 7)).isEqualTo(2.14);
        assertThat(CorrectionService.arrondi(5.0 * 2 / 3)).isEqualTo(3.33);
        assertThat(CorrectionService.arrondi(2.0 + 2.14 + 0.1)).isEqualTo(4.24);
    }
}
