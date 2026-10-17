package sn.ept.eval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "reponses_etudiant")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class ReponseEtudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double noteObtenue;

    @Column(nullable = false)
    private Boolean valideeParProf = false;

    @Column(columnDefinition = "TEXT")
    private String commentaireProf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soumission_id", nullable = false)
    @JsonIgnore
    private Soumission soumission;

    public ReponseEtudiant() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getNoteObtenue() { return noteObtenue; }
    public void setNoteObtenue(Double noteObtenue) { this.noteObtenue = noteObtenue; }

    public Boolean getValideeParProf() { return valideeParProf; }
    public void setValideeParProf(Boolean valideeParProf) { this.valideeParProf = valideeParProf; }

    public String getCommentaireProf() { return commentaireProf; }
    public void setCommentaireProf(String commentaireProf) { this.commentaireProf = commentaireProf; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public Soumission getSoumission() { return soumission; }
    public void setSoumission(Soumission soumission) { this.soumission = soumission; }
}
