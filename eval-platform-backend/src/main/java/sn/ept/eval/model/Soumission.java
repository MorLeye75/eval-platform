package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.StatutSoumission;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "soumissions",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"evaluation_id", "etudiant_id"})
    }
)
public class Soumission extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @Column(nullable = false)
    private LocalDateTime dateDebut = LocalDateTime.now();

    private LocalDateTime dateRemise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutSoumission statut = StatutSoumission.EN_COURS;

    @Column(columnDefinition = "TEXT")
    private String feedbackGeneral;

    @Column(nullable = false)
    private Integer nbChangementsOnglet = 0;

    private Double noteFinale; // /noteFinale

    @OneToMany(mappedBy = "soumission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReponseEtudiant> reponses = new ArrayList<>();

    @OneToMany(mappedBy = "soumission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlerteAntiTriche> alertes = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrateur_id")
    private Administrateur administrateur;

    public Soumission() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Evaluation getEvaluation() { return evaluation; }
    public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }

    public Etudiant getEtudiant() { return etudiant; }
    public void setEtudiant(Etudiant etudiant) { this.etudiant = etudiant; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateRemise() { return dateRemise; }
    public void setDateRemise(LocalDateTime dateRemise) { this.dateRemise = dateRemise; }

    public StatutSoumission getStatut() { return statut; }
    public void setStatut(StatutSoumission statut) { this.statut = statut; }

    public String getFeedbackGeneral() { return feedbackGeneral; }
    public void setFeedbackGeneral(String feedbackGeneral) { this.feedbackGeneral = feedbackGeneral; }

    public Integer getNbChangementsOnglet() { return nbChangementsOnglet; }
    public void setNbChangementsOnglet(Integer nbChangementsOnglet) { this.nbChangementsOnglet = nbChangementsOnglet; }

    public Double getNoteFinale() {
        if (noteFinale != null) {
            return noteFinale;
        }
        if (reponses != null && !reponses.isEmpty()) {
            return reponses.stream()
                .filter(r -> r.getNoteObtenue() != null)
                .mapToDouble(ReponseEtudiant::getNoteObtenue)
                .sum();
        }
        return null;
    }
    public void setNoteFinale(Double noteFinale) { this.noteFinale = noteFinale; }

    public List<ReponseEtudiant> getReponses() { return reponses; }
    public List<ReponseEtudiant> getReponse() { return reponses; }
    public void setReponses(List<ReponseEtudiant> reponses) { this.reponses = reponses; }

    public List<AlerteAntiTriche> getAlertes() { return alertes; }
    public void setAlertes(List<AlerteAntiTriche> alertes) { this.alertes = alertes; }

    public void addReponse(ReponseEtudiant r) {
        reponses.add(r);
        r.setSoumission(this);
    }

    public void addAlerte(AlerteAntiTriche a) {
        alertes.add(a);
        a.setSoumission(this);
    }

    public Administrateur getAdministrateur() { return administrateur; }
    public void setAdministrateur(Administrateur administrateur) { this.administrateur = administrateur; }
}
