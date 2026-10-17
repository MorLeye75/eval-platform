package sn.ept.eval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import sn.ept.eval.model.enums.Role;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "enseignants")
public class Enseignant extends Utilisateur {

    @Column(unique = true)
    private String matricule;

    @OneToMany(mappedBy = "enseignant")
    private List<Evaluation> evaluations = new ArrayList<>();

    @OneToMany(mappedBy = "arbitrePar")
    private List<AlerteAntiTriche> alertesArbitrees = new ArrayList<>();

    public Enseignant() {
        super();
        this.setRole(Role.ENSEIGNANT);
    }

    public Enseignant(String nom, String prenom, String email, String motDePasse, String matricule) {
        super();
        this.setNom(nom);
        this.setPrenom(prenom);
        this.setEmail(email);
        this.setMotDePasse(motDePasse);
        this.setRole(Role.ENSEIGNANT);
        this.matricule = matricule;
    }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public List<Evaluation> getEvaluations() { return evaluations; }
    public void setEvaluations(List<Evaluation> evaluations) { this.evaluations = evaluations; }

    public List<AlerteAntiTriche> getAlertesArbitrees() { return alertesArbitrees; }
    public void setAlertesArbitrees(List<AlerteAntiTriche> alertesArbitrees) { this.alertesArbitrees = alertesArbitrees; }
}
