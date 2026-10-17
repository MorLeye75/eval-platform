package sn.ept.eval.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import sn.ept.eval.model.enums.Role;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "administrateurs")
public class Administrateur extends Utilisateur {

    @OneToMany(mappedBy = "administrateur")
    private List<Soumission> soumissionsSupervisees = new ArrayList<>();

    public Administrateur() {
        super();
        this.setRole(Role.ADMIN);
    }

    public Administrateur(String nom, String prenom, String email, String motDePasse) {
        super();
        this.setNom(nom);
        this.setPrenom(prenom);
        this.setEmail(email);
        this.setMotDePasse(motDePasse);
        this.setRole(Role.ADMIN);
    }

    public List<Soumission> getSoumissionsSupervisees() { return soumissionsSupervisees; }
    public void setSoumissionsSupervisees(List<Soumission> soumissionsSupervisees) { this.soumissionsSupervisees = soumissionsSupervisees; }
}
