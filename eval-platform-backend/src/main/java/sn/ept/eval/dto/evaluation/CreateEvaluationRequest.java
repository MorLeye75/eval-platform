package sn.ept.eval.dto.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Corps du POST de création et du PUT de modification d'une évaluation (DS-03). */
public class CreateEvaluationRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 255, message = "Le titre est limité à 255 caractères")
    private String titre;

    @Size(max = 10000, message = "La description est limitée à 10000 caractères")
    private String description;

    @NotNull(message = "La durée standard est obligatoire")
    @Min(value = 1, message = "La durée doit être d'au moins 1 minute")
    private Integer dureeStandard;

    @NotNull(message = "La date d'ouverture est obligatoire")
    private LocalDateTime dateOuverture;

    @NotNull(message = "La date de fermeture est obligatoire")
    private LocalDateTime dateFermeture;

    @NotEmpty(message = "Choisissez au moins une promotion")
    private List<Long> promotionIds = new ArrayList<>();

    @Valid
    private List<QuestionRequestDTO> questions = new ArrayList<>();

    public CreateEvaluationRequest() {}

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

    public List<Long> getPromotionIds() { return promotionIds; }
    public void setPromotionIds(List<Long> promotionIds) { this.promotionIds = promotionIds; }

    public List<QuestionRequestDTO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionRequestDTO> questions) { this.questions = questions; }
}
