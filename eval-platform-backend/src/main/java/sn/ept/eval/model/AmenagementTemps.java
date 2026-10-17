package sn.ept.eval.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "amenagements_temps",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"etudiant_id", "evaluation_id"})
    }
)
public class AmenagementTemps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @Column(nullable = false)
    private Integer dureePersonnalisee;

    public AmenagementTemps() {}

    public AmenagementTemps(Etudiant etudiant, Evaluation evaluation, Integer dureePersonnalisee) {
        this.etudiant = etudiant;
        this.evaluation = evaluation;
        this.dureePersonnalisee = dureePersonnalisee;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Etudiant getEtudiant() { return etudiant; }
    public void setEtudiant(Etudiant etudiant) { this.etudiant = etudiant; }

    public Evaluation getEvaluation() { return evaluation; }
    public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }

    public Integer getDureePersonnalisee() { return dureePersonnalisee; }
    public void setDureePersonnalisee(Integer dureePersonnalisee) { this.dureePersonnalisee = dureePersonnalisee; }
}
