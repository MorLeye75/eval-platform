package sn.ept.eval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "cas_de_tests")
public class CasDeTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String entree;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sortieAttendue;

    @Column(nullable = false)
    private Boolean estMasque = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @JsonIgnore
    private QuestionCode question;

    @OneToMany(mappedBy = "casDeTest")
    private java.util.List<ResultatCasTest> resultats = new java.util.ArrayList<>();

    public CasDeTest() {}

    public CasDeTest(String entree, String sortieAttendue, Boolean estMasque) {
        this.entree = entree;
        this.sortieAttendue = sortieAttendue;
        this.estMasque = estMasque;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntree() { return entree; }
    public void setEntree(String entree) { this.entree = entree; }

    public String getSortieAttendue() { return sortieAttendue; }
    public void setSortieAttendue(String sortieAttendue) { this.sortieAttendue = sortieAttendue; }

    public Boolean getEstMasque() { return estMasque; }
    public void setEstMasque(Boolean estMasque) { this.estMasque = estMasque; }

    public QuestionCode getQuestion() { return question; }
    public void setQuestion(QuestionCode question) { this.question = question; }

    public java.util.List<ResultatCasTest> getResultats() { return resultats; }
    public void setResultats(java.util.List<ResultatCasTest> resultats) { this.resultats = resultats; }
}
