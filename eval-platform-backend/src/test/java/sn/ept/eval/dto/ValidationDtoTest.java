package sn.ept.eval.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.ept.eval.dto.auth.LoginRequest;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.dto.soumission.SauvegardeReponsesRequest;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationDtoTest {

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
    @DisplayName("Sauvegarde : une réponse texte trop longue est rejetée (validation en cascade sur la liste)")
    void sauvegarde_TexteTropLong_Rejete() {
        String texte = "a".repeat(ReponseItemDto.TAILLE_MAX_TEXTE + 1);
        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest(List.of(new ReponseItemDto(1L, texte, null, null)));

        Set<ConstraintViolation<SauvegardeReponsesRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("reponses[0].texteSoumis");
    }

    @Test
    @DisplayName("Sauvegarde : trop de réponses dans une seule requête est rejeté")
    void sauvegarde_TropDeReponses_Rejete() {
        List<ReponseItemDto> reponses = Collections.nCopies(SauvegardeReponsesRequest.NB_MAX_REPONSES + 1,
                new ReponseItemDto(1L, "ok", null, null));

        assertThat(validator.validate(new SauvegardeReponsesRequest(reponses))).hasSize(1);
    }

    @Test
    @DisplayName("Sauvegarde : une requête de taille normale est acceptée")
    void sauvegarde_Normale_Acceptee() {
        SauvegardeReponsesRequest request = new SauvegardeReponsesRequest(List.of(
                new ReponseItemDto(1L, "réponse", null, null),
                new ReponseItemDto(2L, null, "int main() {}", null),
                new ReponseItemDto(3L, null, null, List.of(10L, 11L))));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("Login : un mot de passe au-delà de la limite BCrypt est rejeté")
    void login_MotDePasseTropLong_Rejete() {
        LoginRequest request = new LoginRequest("etudiant@ept.sn", "x".repeat(73));

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("motDePasse");
    }
}
