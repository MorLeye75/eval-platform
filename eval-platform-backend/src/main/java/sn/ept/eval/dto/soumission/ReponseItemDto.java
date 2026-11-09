package sn.ept.eval.dto.soumission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class ReponseItemDto {

    // Limites : une sauvegarde toutes les 30 s ne doit pas pouvoir saturer le serveur ou la base
    public static final int TAILLE_MAX_TEXTE = 20_000;
    public static final int TAILLE_MAX_CODE = 50_000;
    public static final int NB_MAX_CHOIX = 20;

    private Long questionId;

    @Size(max = TAILLE_MAX_TEXTE, message = "La réponse texte ne peut pas dépasser " + TAILLE_MAX_TEXTE + " caractères")
    private String texteSoumis;

    @Size(max = TAILLE_MAX_CODE, message = "Le code ne peut pas dépasser " + TAILLE_MAX_CODE + " caractères")
    private String codeSoumis;

    /** Langages proposés dans l'éditeur (identifiants Monaco). */
    @Pattern(regexp = "c|cpp|java|python|javascript", message = "Langage non pris en charge")
    private String langage;

    @Size(max = NB_MAX_CHOIX, message = "Trop de choix cochés")
    private List<@NotNull Long> choixIds = new ArrayList<>();

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

    public String getLangage() { return langage; }
    public void setLangage(String langage) { this.langage = langage; }

    public List<Long> getChoixIds() { return choixIds; }
    public void setChoixIds(List<Long> choixIds) { this.choixIds = choixIds; }
}
