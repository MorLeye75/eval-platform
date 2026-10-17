package sn.ept.eval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import sn.ept.eval.model.enums.TypeQuestion;

@Entity
@Table(name = "questions_quiz")
public class QuestionQuiz extends QuestionQCM {

    @Column(nullable = false)
    private Integer dureeQuestion; // en secondes

    public QuestionQuiz() {
        super();
        this.setType(TypeQuestion.QUIZ_CHRONOMETREE);
    }

    public QuestionQuiz(String enonce, Integer ordre, Double points, Boolean estChoixMultiple, Integer dureeQuestion) {
        super(enonce, ordre, points, estChoixMultiple);
        this.setType(TypeQuestion.QUIZ_CHRONOMETREE);
        this.dureeQuestion = dureeQuestion;
    }

    public Integer getDureeQuestion() { return dureeQuestion; }
    public void setDureeQuestion(Integer dureeQuestion) { this.dureeQuestion = dureeQuestion; }
}
