package sn.ept.eval.dto.admin;

import sn.ept.eval.model.enums.NiveauAlerte;

import java.time.LocalDateTime;
import java.util.List;

/** Réponses des trois lectures de la vue d'ensemble de l'administrateur (DS-18). */
public final class VueEnsembleAdminResponses {

    private VueEnsembleAdminResponses() {}

    /** Un état n'est présent que s'il est réellement connu du serveur (jamais inventé). */
    public record Service(String nom, String etat, String detail) {}

    public record ParRole(long etudiants, long enseignants, long admins) {}

    public record TableauDeBord(long nbUtilisateurs, ParRole parRole, long nbEvaluationsEnCours, long nbEvaluationsPubliees,
                                long nbSoumissions7j, long nbAlertes7j, List<Service> services) {}

    public record EvaluationEnCours(Long id, String titre, String enseignant, int nbAttendus, long nbCommences, long nbSoumis,
                                    LocalDateTime dateOuverture, LocalDateTime dateFermeture) {}

    public record Alerte(Long id, Long evaluationId, String evaluation, String etudiant, String description,
                         NiveauAlerte niveau, LocalDateTime date) {}

    public record Supervision(List<EvaluationEnCours> evaluations, List<Alerte> alertes) {}

    /** Indicateurs {@code null} (jamais 0) sans copie notée. */
    public record StatPromotion(Long id, String nom, String annee, long effectif, long nbEvaluations, long nbCopiesNotees,
                                Double moyenneSur20, Double tauxReussite) {}

    public record Statistiques(long nbEvaluations, long nbCopies, long nbCopiesNotees, Double tauxRemise, Double moyenneSur20,
                               Double tauxReussite, List<StatPromotion> promotions) {}
}
