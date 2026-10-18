package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.StatutEvaluation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evaluations")
public class Evaluation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer dureeStandard; // en minutes (dureeStandard du diagramme UML)

    @Column(nullable = false)
    private LocalDateTime dateOuverture;

    @Column(nullable = false)
    private LocalDateTime dateFermeture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutEvaluation statut = StatutEvaluation.BROUILLON;

    @Column(nullable = false)
    private Boolean resultatsPublies = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enseignant_id", nullable = false)
    private Enseignant enseignant;

    @ManyToMany
    @JoinTable(
        name = "evaluation_promotions",
        joinColumns = @JoinColumn(name = "evaluation_id"),
        inverseJoinColumns = @JoinColumn(name = "promotion_id")
    )
    private List<Promotion> promotions = new ArrayList<>();

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Soumission> soumissions = new ArrayList<>();

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AmenagementTemps> amenagements = new ArrayList<>();

    public Evaluation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getDureeStandard() { return dureeStandard; }
    public void setDureeStandard(Integer dureeStandard) { this.dureeStandard = dureeStandard; }

    // Alias pour compatibilité
    public Integer getDuree() { return dureeStandard; }
    public void setDuree(Integer duree) { this.dureeStandard = duree; }

    public LocalDateTime getDateOuverture() { return dateOuverture; }
    public void setDateOuverture(LocalDateTime dateOuverture) { this.dateOuverture = dateOuverture; }

    public LocalDateTime getDateFermeture() { return dateFermeture; }
    public void setDateFermeture(LocalDateTime dateFermeture) { this.dateFermeture = dateFermeture; }

    public StatutEvaluation getStatut() { return statut; }
    public void setStatut(StatutEvaluation statut) { this.statut = statut; }

    public Boolean getResultatsPublies() { return resultatsPublies; }
    public void setResultatsPublies(Boolean resultatsPublies) { this.resultatsPublies = resultatsPublies; }

    public Enseignant getEnseignant() { return enseignant; }
    public void setEnseignant(Enseignant enseignant) { this.enseignant = enseignant; }

    public List<Promotion> getPromotions() { return promotions; }
    public void setPromotions(List<Promotion> promotions) { this.promotions = promotions; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }

    public void addQuestion(Question question) {
        questions.add(question);
        question.setEvaluation(this);
    }

    public List<Soumission> getSoumissions() { return soumissions; }
    public void setSoumissions(List<Soumission> soumissions) { this.soumissions = soumissions; }

    public List<AmenagementTemps> getAmenagements() { return amenagements; }
    public void setAmenagements(List<AmenagementTemps> amenagements) { this.amenagements = amenagements; }
}
