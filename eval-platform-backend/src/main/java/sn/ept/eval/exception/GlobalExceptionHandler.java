package sn.ept.eval.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire d'exceptions centralisé conforme au standard international RFC 7807 (ProblemDetail).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_BASE_URL = "https://eval.ept.sn/errors/";

    @ExceptionHandler(TempsEcouleException.class)
    public ProblemDetail handleTempsEcoule(TempsEcouleException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Temps d'examen dépassé");
        problem.setType(URI.create(ERROR_BASE_URL + "temps-expire"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "TEMPS_EXPIRE");
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    @ExceptionHandler(SoumissionNonModifiableException.class)
    public ProblemDetail handleSoumissionNonModifiable(SoumissionNonModifiableException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Soumission verrouillée");
        problem.setType(URI.create(ERROR_BASE_URL + "soumission-verrouillee"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "SOUMISSION_VERROUILLEE");
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    @ExceptionHandler(AccesRefuseException.class)
    public ProblemDetail handleAccesRefuse(AccesRefuseException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Accès refusé");
        problem.setType(URI.create(ERROR_BASE_URL + "acces-refuse"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ACCES_REFUSE");
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    @ExceptionHandler(RessourceNonTrouveeException.class)
    public ProblemDetail handleRessourceNonTrouvee(RessourceNonTrouveeException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Ressource non trouvée");
        problem.setType(URI.create(ERROR_BASE_URL + "ressource-non-trouvee"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Des erreurs de validation sont survenues.");
        problem.setTitle("Requête invalide");
        problem.setType(URI.create(ERROR_BASE_URL + "validation"));
        problem.setInstance(URI.create(request.getRequestURI()));

        Map<String, String> erreurs = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                erreurs.put(err.getField(), err.getDefaultMessage())
        );
        problem.setProperty("erreursChamps", erreurs);
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requête incorrecte");
        problem.setType(URI.create(ERROR_BASE_URL + "requete-incorrecte"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }
}
