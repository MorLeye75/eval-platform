package sn.ept.eval.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
    }

    @Test
    @DisplayName("RFC 7807 : TempsEcouleException produit un ProblemDetail standardisé avec status 409 et URI")
    void handleTempsEcoule_ConformeRfc7807() {
        request.setRequestURI("/api/soumissions/100/soumettre");
        TempsEcouleException ex = new TempsEcouleException("Le temps imparti au-delà de la marge de tolérance est écoulé. La copie a été figée.");

        ProblemDetail problem = exceptionHandler.handleTempsEcoule(ex, request);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Temps d'examen dépassé");
        assertThat(problem.getDetail()).isEqualTo("Le temps imparti au-delà de la marge de tolérance est écoulé. La copie a été figée.");
        assertThat(problem.getType()).isEqualTo(URI.create("https://eval.ept.sn/errors/temps-expire"));
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/soumissions/100/soumettre"));
        assertThat(problem.getProperties()).containsKey("code");
        assertThat(problem.getProperties().get("code")).isEqualTo("TEMPS_EXPIRE");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("RFC 7807 : SoumissionNonModifiableException renvoie HTTP 409 SOUMISSION_VERROUILLEE")
    void handleSoumissionNonModifiable_ConformeRfc7807() {
        request.setRequestURI("/api/soumissions/100/sauvegarder");
        SoumissionNonModifiableException ex = new SoumissionNonModifiableException("Copie déjà soumise.");

        ProblemDetail problem = exceptionHandler.handleSoumissionNonModifiable(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Soumission verrouillée");
        assertThat(problem.getType()).isEqualTo(URI.create("https://eval.ept.sn/errors/soumission-verrouillee"));
        assertThat(problem.getProperties().get("code")).isEqualTo("SOUMISSION_VERROUILLEE");
    }

    @Test
    @DisplayName("RFC 7807 : RessourceNonTrouveeException renvoie HTTP 404")
    void handleRessourceNonTrouvee_ConformeRfc7807() {
        request.setRequestURI("/api/evaluations/999");
        RessourceNonTrouveeException ex = new RessourceNonTrouveeException("Évaluation introuvable");

        ProblemDetail problem = exceptionHandler.handleRessourceNonTrouvee(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Ressource non trouvée");
        assertThat(problem.getType()).isEqualTo(URI.create("https://eval.ept.sn/errors/ressource-non-trouvee"));
    }
}
