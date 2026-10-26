package sn.ept.eval.dto.evaluation;

import sn.ept.eval.model.enums.TypeQuestion;

import java.util.ArrayList;
import java.util.List;

/** Question vue par l'enseignant : contient les bonnes réponses, ne doit JAMAIS être servie à un étudiant. */
public class QuestionResponseDTO {

    private Long id;
    private TypeQuestion type;
    private String enonce;
    private Integer ordre;
    private Double points;
    private Boolean estChoixMultiple;
    private Integer dureeQuestion;
    private List<ChoixDTO> choix = new ArrayList<>();
    private List<CasDeTestDTO> casTests = new ArrayList<>();

    public QuestionResponseDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TypeQuestion getType() { return type; }
    public void setType(TypeQuestion type) { this.type = type; }

    public String getEnonce() { return enonce; }
    public void setEnonce(String enonce) { this.enonce = enonce; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

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
