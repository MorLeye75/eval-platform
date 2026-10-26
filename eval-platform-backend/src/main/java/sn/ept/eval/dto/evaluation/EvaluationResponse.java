package sn.ept.eval.dto.evaluation;

import sn.ept.eval.model.enums.StatutEvaluation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Évaluation vue par son enseignant (DS-03, DS-04). Dans la liste, {@code questions} est vide
 * et seul {@code nbQuestions} est renseigné ; le détail contient les questions et les bonnes réponses.
 */
public class EvaluationResponse {

    private Long id;
    private String titre;
    private String description;
    private Integer dureeStandard;
    private LocalDateTime dateOuverture;
    private LocalDateTime dateFermeture;
    private StatutEvaluation statut;
    private Boolean resultatsPublies;
    private Long version;
    private List<Long> promotionIds = new ArrayList<>();
    private Integer nbQuestions;
    private List<QuestionResponseDTO> questions = new ArrayList<>();
    private List<AmenagementResponse> amenagements = new ArrayList<>();

    public EvaluationResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getDureeStandard() { return dureeStandard; }
    public void setDureeStandard(Integer dureeStandard) { this.dureeStandard = dureeStandard; }

    public LocalDateTime getDateOuverture() { return dateOuverture; }
    public void setDateOuverture(LocalDateTime dateOuverture) { this.dateOuverture = dateOuverture; }

    public LocalDateTime getDateFermeture() { return dateFermeture; }
    public void setDateFermeture(LocalDateTime dateFermeture) { this.dateFermeture = dateFermeture; }

    public StatutEvaluation getStatut() { return statut; }
    public void setStatut(StatutEvaluation statut) { this.statut = statut; }

    public Boolean getResultatsPublies() { return resultatsPublies; }
    public void setResultatsPublies(Boolean resultatsPublies) { this.resultatsPublies = resultatsPublies; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<Long> getPromotionIds() { return promotionIds; }
    public void setPromotionIds(List<Long> promotionIds) { this.promotionIds = promotionIds; }

    public Integer getNbQuestions() { return nbQuestions; }
    public void setNbQuestions(Integer nbQuestions) { this.nbQuestions = nbQuestions; }

    public List<QuestionResponseDTO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionResponseDTO> questions) { this.questions = questions; }

    public List<AmenagementResponse> getAmenagements() { return amenagements; }
    public void setAmenagements(List<AmenagementResponse> amenagements) { this.amenagements = amenagements; }
}
