package sn.ept.eval.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import sn.ept.eval.model.enums.TypeQuestion;

@Entity
@Table(name = "questions_ouvertes")
public class QuestionOuverte extends Question {

    public QuestionOuverte() {
        super();
        this.setType(TypeQuestion.OUVERTE);
    }

    public QuestionOuverte(String enonce, Integer ordre, Double points) {
        super(null, enonce, ordre, points, TypeQuestion.OUVERTE, null);
    }
}
