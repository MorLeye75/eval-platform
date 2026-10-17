package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.TypeQuestion;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions_qcm")
public class QuestionQCM extends Question {

    @Column(nullable = false)
    private Boolean estChoixMultiple = false;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChoixReponse> choix = new ArrayList<>();

    public QuestionQCM() {
        super();
        this.setType(TypeQuestion.QCM);
    }

    public QuestionQCM(String enonce, Integer ordre, Double points, Boolean estChoixMultiple) {
        super(null, enonce, ordre, points, TypeQuestion.QCM, null);
        this.estChoixMultiple = estChoixMultiple;
    }

    public Boolean getEstChoixMultiple() { return estChoixMultiple; }
    public void setEstChoixMultiple(Boolean estChoixMultiple) { this.estChoixMultiple = estChoixMultiple; }

    public List<ChoixReponse> getChoix() { return choix; }
    public void setChoix(List<ChoixReponse> choix) { this.choix = choix; }

    public void addChoix(ChoixReponse c) {
        choix.add(c);
        c.setQuestion(this);
    }
}
