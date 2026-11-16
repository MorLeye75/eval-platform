package sn.ept.eval.dto.evaluation;

import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutEvaluation;

import java.time.LocalDateTime;
import java.util.List;

/** Avancement d'une évaluation en cours, en lecture seule (DS-17) : ni contenu des réponses ni notes. */
public record SupervisionResponse(
        Long evaluationId, String titre, StatutEvaluation statut, LocalDateTime dateOuverture, LocalDateTime dateFermeture,
        int nbQuestions, int nbAttendus, int nbCommences, int nbSoumis, int nbAlertes,
        List<EtudiantSupervise> etudiants, List<AlerteSupervision> alertes) {

    public enum EtatCopie { NON_COMMENCE, EN_COURS, SOUMIS }

    /** {@code nbReponses} : réponses réellement données (une réponse vide enregistrée par la sauvegarde automatique ne compte pas). */
    public record EtudiantSupervise(Long etudiantId, String nom, String prenom, EtatCopie etat, int nbReponses,
                                    int nbChangementsOnglet, int nbAlertes) {}

    public record AlerteSupervision(Long id, Long etudiantId, String etudiant, String description, NiveauAlerte niveau,
                                    LocalDateTime date) {}
}
