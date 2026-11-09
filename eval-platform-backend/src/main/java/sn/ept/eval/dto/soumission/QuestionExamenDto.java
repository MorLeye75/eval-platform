package sn.ept.eval.dto.soumission;

import sn.ept.eval.model.enums.TypeQuestion;

import java.util.ArrayList;
import java.util.List;

public class QuestionExamenDto {

    private Long id;
    private String enonce;
    private Integer ordre;
    private Double points;
    private TypeQuestion type;
    private Boolean estChoixMultiple;
    private List<ChoixItemDto> choixPossibles = new ArrayList<>();
    private List<CasDeTestVisibleDto> casDeTestsVisibles = new ArrayList<>();
    private ReponseItemDto reponseActuelle;
    /** Secondes accordées à la question (quiz chronométré seulement). */
    private Integer dureeQuestion;

    public QuestionExamenDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEnonce() { return enonce; }
    public void setEnonce(String enonce) { this.enonce = enonce; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public Double getPoints() { return points; }
    public void setPoints(Double points) { this.points = points; }

    public TypeQuestion getType() { return type; }
    public void setType(TypeQuestion type) { this.type = type; }

    public Boolean getEstChoixMultiple() { return estChoixMultiple; }
    public void setEstChoixMultiple(Boolean estChoixMultiple) { this.estChoixMultiple = estChoixMultiple; }

    public List<ChoixItemDto> getChoixPossibles() { return choixPossibles; }
    public void setChoixPossibles(List<ChoixItemDto> choixPossibles) { this.choixPossibles = choixPossibles; }

    public List<CasDeTestVisibleDto> getCasDeTestsVisibles() { return casDeTestsVisibles; }
    public void setCasDeTestsVisibles(List<CasDeTestVisibleDto> casDeTestsVisibles) { this.casDeTestsVisibles = casDeTestsVisibles; }

    public ReponseItemDto getReponseActuelle() { return reponseActuelle; }
    public void setReponseActuelle(ReponseItemDto reponseActuelle) { this.reponseActuelle = reponseActuelle; }

    public Integer getDureeQuestion() { return dureeQuestion; }
    public void setDureeQuestion(Integer dureeQuestion) { this.dureeQuestion = dureeQuestion; }
}
