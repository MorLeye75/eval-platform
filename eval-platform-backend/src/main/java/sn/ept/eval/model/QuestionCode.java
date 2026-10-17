package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.TypeQuestion;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions_code")
public class QuestionCode extends Question {

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CasDeTest> casTests = new ArrayList<>();

    public QuestionCode() {
        super();
        this.setType(TypeQuestion.CODE);
    }

    public QuestionCode(String enonce, Integer ordre, Double points) {
        super(null, enonce, ordre, points, TypeQuestion.CODE, null);
    }

    public List<CasDeTest> getCasTests() { return casTests; }
    public void setCasTests(List<CasDeTest> casTests) { this.casTests = casTests; }

    public void addCasDeTest(CasDeTest c) {
        casTests.add(c);
        c.setQuestion(this);
    }
}
