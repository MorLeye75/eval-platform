package sn.ept.eval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import sn.ept.eval.model.enums.TypeQuestion;

@Entity
@Table(name = "questions")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enonce;

    @Column(nullable = false)
    private Integer ordre;

    @Column(nullable = false)
    private Double points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeQuestion type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false)
    @JsonIgnore
    private Evaluation evaluation;

    @OneToMany(mappedBy = "question")
    private java.util.List<ReponseEtudiant> reponses = new java.util.ArrayList<>();

    public Question() {}

    public Question(Long id, String enonce, Integer ordre, Double points, TypeQuestion type, Evaluation evaluation) {
        this.id = id;
        this.enonce = enonce;
        this.ordre = ordre;
        this.points = points;
        this.type = type;
        this.evaluation = evaluation;
    }

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

    public Evaluation getEvaluation() { return evaluation; }
    public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }

    public java.util.List<ReponseEtudiant> getReponses() { return reponses; }
    public void setReponses(java.util.List<ReponseEtudiant> reponses) { this.reponses = reponses; }
}
