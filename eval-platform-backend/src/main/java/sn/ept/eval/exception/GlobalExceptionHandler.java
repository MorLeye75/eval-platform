package sn.ept.eval.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire d'exceptions centralisé conforme au standard international RFC 7807 (ProblemDetail).
 * Hérite de ResponseEntityExceptionHandler pour que les erreurs Spring MVC standard
 * (JSON illisible, méthode non supportée, ressource absente...) gardent leur statut 4xx.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URL = "https://eval.ept.sn/errors/";

    /**
     * 409 Conflict (et non 403) : l'utilisateur a bien le droit d'agir sur sa copie,
     * c'est l'état de la ressource (temps écoulé, copie verrouillée) qui empêche l'action.
     */
    @ExceptionHandler(TempsEcouleException.class)
    public ProblemDetail handleTempsEcoule(TempsEcouleException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Temps d'examen dépassé");
        problem.setType(URI.create(ERROR_BASE_URL + "temps-expire"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "TEMPS_EXPIRE");
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    @ExceptionHandler(SoumissionNonModifiableException.class)
    public ProblemDetail handleSoumissionNonModifiable(SoumissionNonModifiableException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requête incorrecte");
        problem.setType(URI.create(ERROR_BASE_URL + "requete-incorrecte"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    /** Échec du login : message volontairement générique pour ne pas révéler si l'email existe. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Identifiants invalides ou compte désactivé.");
        problem.setTitle("Échec de l'authentification");
        problem.setType(URI.create(ERROR_BASE_URL + "authentification"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "AUTHENTIFICATION_ECHOUEE");
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    /** Force brute sur le login : 429 avec l'en-tête standard Retry-After. */
    @ExceptionHandler(TropDeTentativesException.class)
    public ResponseEntity<ProblemDetail> handleTropDeTentatives(TropDeTentativesException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Trop de tentatives");
        problem.setType(URI.create(ERROR_BASE_URL + "trop-de-tentatives"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "TROP_DE_TENTATIVES");
        problem.setProperty("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSecondesAvantNouvelEssai()))
                .body(problem);
    }

    /** Refus d'un @PreAuthorize (rôle insuffisant). */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Votre rôle ne permet pas d'accéder à cette ressource.");
        problem.setTitle("Accès refusé");
        problem.setType(URI.create(ERROR_BASE_URL + "acces-refuse"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ACCES_REFUSE");
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    /** Filet de sécurité : toute erreur imprévue devient un 500 sans fuite de détails internes. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInattendue(Exception ex, HttpServletRequest request) {
        log.error("Erreur inattendue sur {}", request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue.");
        problem.setTitle("Erreur interne");
        problem.setType(URI.create(ERROR_BASE_URL + "erreur-interne"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        return ResponseEntity.badRequest().body(handleValidation(ex, servletRequest));
    }

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
}
