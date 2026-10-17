package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.Role;

@Entity
@Table(name = "etudiants")
public class Etudiant extends Utilisateur {

    @Column(unique = true)
    private String numEtudiant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @OneToMany(mappedBy = "etudiant")
    private java.util.List<Soumission> soumissions = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "etudiant")
    private java.util.List<AmenagementTemps> amenagements = new java.util.ArrayList<>();

    public Etudiant() {
        super();
        this.setRole(Role.ETUDIANT);
    }

    public Etudiant(String nom, String prenom, String email, String motDePasse, String numEtudiant, Promotion promotion) {
        super();
        this.setNom(nom);
        this.setPrenom(prenom);
        this.setEmail(email);
        this.setMotDePasse(motDePasse);
        this.setRole(Role.ETUDIANT);
        this.numEtudiant = numEtudiant;
        this.promotion = promotion;
    }

    public String getNumEtudiant() { return numEtudiant; }
    public void setNumEtudiant(String numEtudiant) { this.numEtudiant = numEtudiant; }

    public Promotion getPromotion() { return promotion; }
    public void setPromotion(Promotion promotion) { this.promotion = promotion; }

    public java.util.List<Soumission> getSoumissions() { return soumissions; }
    public void setSoumissions(java.util.List<Soumission> soumissions) { this.soumissions = soumissions; }

    public java.util.List<AmenagementTemps> getAmenagements() { return amenagements; }
    public void setAmenagements(java.util.List<AmenagementTemps> amenagements) { this.amenagements = amenagements; }
}
