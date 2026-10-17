package sn.ept.eval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "choix_reponses")
public class ChoixReponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Column(nullable = false)
    private Boolean estCorrect = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @JsonIgnore
    private QuestionQCM question;

    @ManyToMany(mappedBy = "choixCoches")
    private java.util.List<ReponseQCM> reponsesCochees = new java.util.ArrayList<>();

    public ChoixReponse() {}

    public ChoixReponse(String texte, Boolean estCorrect) {
        this.texte = texte;
        this.estCorrect = estCorrect;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public Boolean getEstCorrect() { return estCorrect; }
    public void setEstCorrect(Boolean estCorrect) { this.estCorrect = estCorrect; }

    public QuestionQCM getQuestion() { return question; }
    public void setQuestion(QuestionQCM question) { this.question = question; }

    public java.util.List<ReponseQCM> getReponsesCochees() { return reponsesCochees; }
    public void setReponsesCochees(java.util.List<ReponseQCM> reponsesCochees) { this.reponsesCochees = reponsesCochees; }
}
