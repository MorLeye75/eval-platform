package sn.ept.eval.model;

import jakarta.persistence.*;

@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String annee;

    @OneToMany(mappedBy = "promotion")
    private java.util.List<Etudiant> etudiants = new java.util.ArrayList<>();

    @ManyToMany(mappedBy = "promotions")
    private java.util.List<Evaluation> evaluations = new java.util.ArrayList<>();

    public Promotion() {}

    public Promotion(String nom, String annee) {
        this.nom = nom;
        this.annee = annee;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getAnnee() { return annee; }
    public void setAnnee(String annee) { this.annee = annee; }

    public java.util.List<Etudiant> getEtudiants() { return etudiants; }
    public void setEtudiants(java.util.List<Etudiant> etudiants) { this.etudiants = etudiants; }

    public java.util.List<Evaluation> getEvaluations() { return evaluations; }
    public void setEvaluations(java.util.List<Evaluation> evaluations) { this.evaluations = evaluations; }

    /**
     * Égalité par identifiant (motif Hibernate) : deux instances de la même ligne, proxy compris, sont égales.
     * hashCode constant par classe pour rester stable quand l'identifiant est attribué à l'enregistrement.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || org.hibernate.Hibernate.getClass(this) != org.hibernate.Hibernate.getClass(o)) return false;
        Long autreId = ((Promotion) o).getId();
        return getId() != null && getId().equals(autreId);
    }

    @Override
    public final int hashCode() {
        return org.hibernate.Hibernate.getClass(this).hashCode();
    }
}
