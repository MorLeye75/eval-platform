package sn.ept.eval.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    /** Horloge de l'application figée : l'horodatage des erreurs en vient, comme le reste (n° 11 de la revue). */
    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 11, 14, 10, 0);
    private static final Clock HORLOGE = Clock.fixed(MAINTENANT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler(HORLOGE);
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
    @DisplayName("Horodatage d'une erreur : celui de l'horloge de l'application (Clock), pas l'heure système")
    void horodatage_HorlogeDeLApplication() {
        request.setRequestURI("/api/soumissions/100/soumettre");

        ProblemDetail problem = exceptionHandler.handleTempsEcoule(new TempsEcouleException("Temps écoulé."), request);

        assertThat(problem.getProperties().get("timestamp")).isEqualTo(MAINTENANT);
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
    @DisplayName("RFC 7807 : VersionObsoleteException renvoie HTTP 409 VERSION_OBSOLETE avec la version actuelle")
    void handleVersionObsolete_ConformeRfc7807() {
        request.setRequestURI("/api/soumissions/100/sauvegarder");

        ProblemDetail problem = exceptionHandler.handleVersionObsolete(new VersionObsoleteException("Copie modifiée.", 4), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).isEqualTo(URI.create("https://eval.ept.sn/errors/version-obsolete"));
        assertThat(problem.getProperties().get("code")).isEqualTo("VERSION_OBSOLETE");
        assertThat(problem.getProperties().get("versionActuelle")).isEqualTo(4);
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

    @Test
    @DisplayName("RFC 7807 : chaque erreur porte un code, y compris 400, 404 et 500")
    void codePresentPartout() {
        request.setRequestURI("/api/x");

        assertThat(exceptionHandler.handleRessourceNonTrouvee(new RessourceNonTrouveeException("absente"), request)
                .getProperties()).containsEntry("code", "RESSOURCE_NON_TROUVEE");
        assertThat(exceptionHandler.handleIllegalArgument(new IllegalArgumentException("mauvaise"), request)
                .getProperties()).containsEntry("code", "REQUETE_INCORRECTE");
        assertThat(exceptionHandler.handleInattendue(new RuntimeException("panne"), request)
                .getProperties()).containsEntry("code", "ERREUR_INTERNE");
    }

    @Test
    @DisplayName("Contrainte de base violée : 409 CONFLIT_DONNEES, sans nom de contrainte ni SQL dans la réponse")
    void integriteDonnees_409() {
        request.setRequestURI("/api/soumissions/100/sauvegarder");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \"uk_reponses_soumission_question\"]");

        ProblemDetail problem = exceptionHandler.handleIntegriteDonnees(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "CONFLIT_DONNEES");
        assertThat(problem.getDetail()).doesNotContain("uk_reponses", "duplicate", "statement");
    }

    @Test
    @DisplayName("Verrou indisponible (attente dépassée) : 409 VERROU_INDISPONIBLE à réessayer, et non 500")
    void verrouIndisponible_409() {
        request.setRequestURI("/api/soumissions/100/soumettre");

        ProblemDetail problem = exceptionHandler.handleVerrouIndisponible(
                new PessimisticLockingFailureException("lock timeout"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "VERROU_INDISPONIBLE");
        assertThat(problem.getDetail()).contains("Réessayez");
    }
}
