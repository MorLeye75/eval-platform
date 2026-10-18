package sn.ept.eval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import sn.ept.eval.model.enums.NiveauAlerte;

import java.time.LocalDateTime;

@Entity
@Table(name = "alertes_anti_triche")
public class AlerteAntiTriche extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiveauAlerte niveau;

    @Column(nullable = false)
    private LocalDateTime dateDetection = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soumission_id", nullable = false)
    @JsonIgnore
    private Soumission soumission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enseignant_id")
    private Enseignant arbitrePar;

    public AlerteAntiTriche() {}

    public AlerteAntiTriche(String description, NiveauAlerte niveau, Soumission soumission) {
        this.description = description;
        this.niveau = niveau;
        this.soumission = soumission;
        this.dateDetection = LocalDateTime.now();
    }

    public AlerteAntiTriche(String description, NiveauAlerte niveau, Soumission soumission, Enseignant arbitrePar) {
        this(description, niveau, soumission);
        this.arbitrePar = arbitrePar;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public NiveauAlerte getNiveau() { return niveau; }
    public void setNiveau(NiveauAlerte niveau) { this.niveau = niveau; }

    public LocalDateTime getDateDetection() { return dateDetection; }
    public void setDateDetection(LocalDateTime dateDetection) { this.dateDetection = dateDetection; }

    public Soumission getSoumission() { return soumission; }
    public void setSoumission(Soumission soumission) { this.soumission = soumission; }

    public Enseignant getArbitrePar() { return arbitrePar; }
    public void setArbitrePar(Enseignant arbitrePar) { this.arbitrePar = arbitrePar; }
}
