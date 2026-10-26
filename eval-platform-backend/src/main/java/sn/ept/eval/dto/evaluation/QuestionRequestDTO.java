package sn.ept.eval.dto.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sn.ept.eval.model.enums.TypeQuestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Question reçue à la création ou à la modification d'une évaluation (DS-03).
 * Les règles propres à chaque type (nombre de choix, cas de test...) sont vérifiées par EvaluationService.
 * L'ordre n'est pas fourni : c'est la position dans la liste.
 */
public class QuestionRequestDTO {

    @NotNull(message = "Le type de question est obligatoire")
    private TypeQuestion type;

    @NotBlank(message = "L'énoncé est obligatoire")
    @Size(max = 10000, message = "L'énoncé est limité à 10000 caractères")
    private String enonce;

    @NotNull(message = "Les points sont obligatoires")
    @Positive(message = "Les points doivent être supérieurs à 0")
    private Double points;

    /** QCM et quiz uniquement. */
    private Boolean estChoixMultiple = false;

    /** Quiz chronométré uniquement : secondes par question. */
    private Integer dureeQuestion;

    @Valid
    private List<ChoixDTO> choix = new ArrayList<>();

    @Valid
    private List<CasDeTestDTO> casTests = new ArrayList<>();

    public QuestionRequestDTO() {}

    public TypeQuestion getType() { return type; }
    public void setType(TypeQuestion type) { this.type = type; }

    public String getEnonce() { return enonce; }
    public void setEnonce(String enonce) { this.enonce = enonce; }

    public Double getPoints() { return points; }
    public void setPoints(Double points) { this.points = points; }

    public Boolean getEstChoixMultiple() { return estChoixMultiple; }
    public void setEstChoixMultiple(Boolean estChoixMultiple) { this.estChoixMultiple = estChoixMultiple; }

    public Integer getDureeQuestion() { return dureeQuestion; }
    public void setDureeQuestion(Integer dureeQuestion) { this.dureeQuestion = dureeQuestion; }

    public List<ChoixDTO> getChoix() { return choix; }
    public void setChoix(List<ChoixDTO> choix) { this.choix = choix; }

    public List<CasDeTestDTO> getCasTests() { return casTests; }
    public void setCasTests(List<CasDeTestDTO> casTests) { this.casTests = casTests; }
}
