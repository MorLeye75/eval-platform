package sn.ept.eval.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.ept.eval.dto.evaluation.AmenagementRequestDTO;
import sn.ept.eval.dto.evaluation.ChoixDTO;
import sn.ept.eval.dto.evaluation.CreateEvaluationRequest;
import sn.ept.eval.dto.evaluation.QuestionRequestDTO;
import sn.ept.eval.model.enums.TypeQuestion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Contraintes de forme des DTO de création d'évaluation (DS-03) et d'aménagement (DS-06). */
class EvaluationDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    @DisplayName("Requête complète : aucune violation")
    void requeteValide() {
        assertThat(validator.validate(requeteValide(List.of(question())))).isEmpty();
    }

    @Test
    @DisplayName("Titre vide, durée nulle, dates absentes et aucune promotion : toutes les erreurs sont remontées")
    void champsObligatoires() {
        CreateEvaluationRequest r = new CreateEvaluationRequest();
        r.setTitre(" ");
        r.setDureeStandard(0);

        Set<ConstraintViolation<CreateEvaluationRequest>> violations = validator.validate(r);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("titre", "dureeStandard", "dateOuverture", "dateFermeture", "promotionIds");
    }

    @Test
    @DisplayName("Question : la validation descend dans la liste (énoncé vide, points négatifs, type absent)")
    void questionEnCascade() {
        QuestionRequestDTO q = new QuestionRequestDTO();
        q.setEnonce("");
        q.setPoints(-1.0);

        Set<ConstraintViolation<CreateEvaluationRequest>> violations = validator.validate(requeteValide(List.of(q)));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("questions[0].type", "questions[0].enonce", "questions[0].points");
    }

    @Test
    @DisplayName("Choix : texte vide et estCorrect absent rejetés, même imbriqués dans une question")
    void choixEnCascade() {
        QuestionRequestDTO q = question();
        q.setChoix(List.of(new ChoixDTO("", null)));

        Set<ConstraintViolation<CreateEvaluationRequest>> violations = validator.validate(requeteValide(List.of(q)));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("questions[0].choix[0].texte", "questions[0].choix[0].estCorrect");
    }

    @Test
    @DisplayName("Aménagement : durée obligatoire et au moins 1 minute")
    void amenagement() {
        assertThat(validator.validate(new AmenagementRequestDTO(null))).hasSize(1);
        assertThat(validator.validate(new AmenagementRequestDTO(0))).hasSize(1);
        assertThat(validator.validate(new AmenagementRequestDTO(80))).isEmpty();
    }

    private CreateEvaluationRequest requeteValide(List<QuestionRequestDTO> questions) {
        CreateEvaluationRequest r = new CreateEvaluationRequest();
        r.setTitre("Réseaux");
        r.setDureeStandard(60);
        r.setDateOuverture(LocalDateTime.of(2026, 11, 1, 8, 0));
        r.setDateFermeture(LocalDateTime.of(2026, 11, 1, 12, 0));
        r.setPromotionIds(List.of(5L));
        r.setQuestions(questions);
        return r;
    }

    private QuestionRequestDTO question() {
        QuestionRequestDTO q = new QuestionRequestDTO();
        q.setType(TypeQuestion.QCM);
        q.setEnonce("Quel protocole ?");
        q.setPoints(2.0);
        q.setChoix(List.of(new ChoixDTO("TCP", true), new ChoixDTO("UDP", false)));
        return q;
    }
}
