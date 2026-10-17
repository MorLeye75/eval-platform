package sn.ept.eval.dto.soumission;

import java.util.ArrayList;
import java.util.List;

public class ReponseItemDto {

    private Long questionId;
    private String texteSoumis;
    private String codeSoumis;
    private List<Long> choixIds = new ArrayList<>();

    public ReponseItemDto() {}

    public ReponseItemDto(Long questionId, String texteSoumis, String codeSoumis, List<Long> choixIds) {
        this.questionId = questionId;
        this.texteSoumis = texteSoumis;
        this.codeSoumis = codeSoumis;
        if (choixIds != null) {
            this.choixIds = choixIds;
        }
    }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getTexteSoumis() { return texteSoumis; }
    public void setTexteSoumis(String texteSoumis) { this.texteSoumis = texteSoumis; }

    public String getCodeSoumis() { return codeSoumis; }
    public void setCodeSoumis(String codeSoumis) { this.codeSoumis = codeSoumis; }

    public List<Long> getChoixIds() { return choixIds; }
    public void setChoixIds(List<Long> choixIds) { this.choixIds = choixIds; }
}
