package sn.ept.eval.dto.etudiant;

import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.TypeQuestion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Évaluation vue par l'étudiant dans sa liste (DS-10) : jamais de question ni de réponse. */
public class EvaluationEtudiantResponse {

    private Long id;
    private String titre;
    private String description;
    private StatutEvaluation statut;
    private LocalDateTime dateOuverture;
    private LocalDateTime dateFermeture;
    private Integer dureeMinutes; // durée effective : tiers-temps de l'étudiant s'il existe, sinon durée standard
    private Integer dureeQuestion; // secondes par question, uniquement pour un quiz chronométré
    private Integer nbQuestions;
    private Double totalPoints; // barème : somme des points des questions
    private Double noteFinale; // seulement si les résultats sont publiés et la copie corrigée
    private List<TypeQuestion> typesQuestions = new ArrayList<>();
    private Boolean resultatsPublies;
    private Long soumissionId; // null tant que l'étudiant n'a pas démarré
    private StatutSoumission statutSoumission;

    public EvaluationEtudiantResponse() {}

    public Double getTotalPoints() { return totalPoints; }
    public void setTotalPoints(Double totalPoints) { this.totalPoints = totalPoints; }

    public Double getNoteFinale() { return noteFinale; }
    public void setNoteFinale(Double noteFinale) { this.noteFinale = noteFinale; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public StatutEvaluation getStatut() { return statut; }
    public void setStatut(StatutEvaluation statut) { this.statut = statut; }

    public LocalDateTime getDateOuverture() { return dateOuverture; }
    public void setDateOuverture(LocalDateTime dateOuverture) { this.dateOuverture = dateOuverture; }

    public LocalDateTime getDateFermeture() { return dateFermeture; }
    public void setDateFermeture(LocalDateTime dateFermeture) { this.dateFermeture = dateFermeture; }

    public Integer getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(Integer dureeMinutes) { this.dureeMinutes = dureeMinutes; }

    public Integer getDureeQuestion() { return dureeQuestion; }
    public void setDureeQuestion(Integer dureeQuestion) { this.dureeQuestion = dureeQuestion; }

    public Integer getNbQuestions() { return nbQuestions; }
    public void setNbQuestions(Integer nbQuestions) { this.nbQuestions = nbQuestions; }

    public List<TypeQuestion> getTypesQuestions() { return typesQuestions; }
    public void setTypesQuestions(List<TypeQuestion> typesQuestions) { this.typesQuestions = typesQuestions; }

    public Boolean getResultatsPublies() { return resultatsPublies; }
    public void setResultatsPublies(Boolean resultatsPublies) { this.resultatsPublies = resultatsPublies; }

    public Long getSoumissionId() { return soumissionId; }
    public void setSoumissionId(Long soumissionId) { this.soumissionId = soumissionId; }

    public StatutSoumission getStatutSoumission() { return statutSoumission; }
    public void setStatutSoumission(StatutSoumission statutSoumission) { this.statutSoumission = statutSoumission; }
}
