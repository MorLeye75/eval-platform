package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.SoumissionRepository;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Règles de la correction automatique QCM : bonne réponse exacte ou zéro, note finale seulement si toutes les
 * questions sont des QCM, idempotence, et annulation de la transaction en cas d'erreur.
 */
@ExtendWith(MockitoExtension.class)
class CorrectionServiceTest {

    @Mock
    private SoumissionRepository soumissionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

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

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new CorrectionService(soumissionRepository, transactionManager);

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
        verify(transactionManager).commit(any());
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

    @Test
    @DisplayName("Copie déjà corrigée : rien n'est recalculé (la note de l'enseignant est conservée)")
    void dejaCorrigee_Ignoree() throws Exception {
        soumission.setStatut(StatutSoumission.EVALUE);
        soumission.setNoteFinale(4.5);
        reponse(simple, b);

        corriger();

        assertThat(soumission.getNoteFinale()).isEqualTo(4.5);
        verify(soumissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Erreur pendant la correction : la transaction est annulée, jamais validée")
    void erreur_Rollback() throws Exception {
        when(soumissionRepository.save(any())).thenThrow(new IllegalStateException("base indisponible"));
        reponse(simple, a);

        corriger(); // l'erreur est journalisée, pas propagée

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }
}
