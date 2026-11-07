package sn.ept.eval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.QuestionOuverte;
import sn.ept.eval.model.ReponseEtudiant;
import sn.ept.eval.model.ReponseOuverte;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.repository.ChoixReponseRepository;
import sn.ept.eval.repository.QuestionRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnregistrementReponsesServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ChoixReponseRepository choixReponseRepository;

    @Mock
    private ReponseEtudiantRepository reponseEtudiantRepository;

    private EnregistrementReponsesService service;
    private Soumission soumission;

    @BeforeEach
    void setUp() {
        service = new EnregistrementReponsesService(questionRepository, choixReponseRepository, reponseEtudiantRepository);

        Evaluation evaluation = new Evaluation();
        evaluation.setId(10L);
        soumission = new Soumission();
        soumission.setId(100L);
        soumission.setEvaluation(evaluation);
    }

    private QuestionOuverte questionOuverte(Long id) {
        QuestionOuverte question = new QuestionOuverte("Expliquez la récursivité.", 1, 5.0);
        question.setId(id);
        return question;
    }

    @Test
    @DisplayName("DS-02 : une question envoyée plusieurs fois dans la même sauvegarde est refusée sans rien enregistrer")
    void enregistrer_QuestionEnDouble_Refusee() {
        List<ReponseItemDto> items = List.of(
                new ReponseItemDto(1L, "première", null, null),
                new ReponseItemDto(1L, "seconde", null, null));

        assertThatThrownBy(() -> service.enregistrer(soumission, items))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question 1")
                .hasMessageContaining("plusieurs fois");
        verify(reponseEtudiantRepository, never()).save(any());
    }

    @Test
    @DisplayName("DS-02 : une réponse existante est mise à jour, pas dupliquée")
    void enregistrer_ReponseExistante_MiseAJour() {
        QuestionOuverte question = questionOuverte(1L);
        ReponseOuverte existante = new ReponseOuverte();
        existante.setQuestion(question);
        existante.setSoumission(soumission);
        existante.setTexteSoumis("ancienne");
        when(questionRepository.findByEvaluationIdOrderByOrdreAsc(10L)).thenReturn(List.of(question));
        when(reponseEtudiantRepository.findBySoumissionId(100L)).thenReturn(List.<ReponseEtudiant>of(existante));

        int nb = service.enregistrer(soumission, List.of(new ReponseItemDto(1L, "nouvelle", null, null)));

        assertThat(nb).isEqualTo(1);
        assertThat(existante.getTexteSoumis()).isEqualTo("nouvelle");
        verify(reponseEtudiantRepository, times(1)).save(existante);
    }

    @Test
    @DisplayName("Question hors de l'évaluation, existante ailleurs ou non : même refus, sans révéler laquelle existe")
    void questionHorsEvaluation_MemeRefus() {
        when(questionRepository.findByEvaluationIdOrderByOrdreAsc(10L)).thenReturn(List.of(questionOuverte(1L)));
        lenient().when(questionRepository.existsById(77L)).thenReturn(true);   // question d'une autre évaluation
        lenient().when(questionRepository.existsById(999L)).thenReturn(false); // identifiant inexistant

        for (Long id : List.of(77L, 999L)) {
            assertThatThrownBy(() -> service.enregistrer(soumission, List.of(new ReponseItemDto(id, "x", null, null))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La question " + id + " n'appartient pas à cette évaluation.");
        }
        verify(questionRepository, never()).existsById(any());
        verify(reponseEtudiantRepository, never()).save(any());
    }
}
