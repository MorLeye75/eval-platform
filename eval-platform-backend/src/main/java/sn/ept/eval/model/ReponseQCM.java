package sn.ept.eval.model;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "reponses_qcm")
public class ReponseQCM extends ReponseEtudiant {

    @ManyToMany
    @JoinTable(
        name = "reponse_qcm_choix",
        joinColumns = @JoinColumn(name = "reponse_qcm_id"),
        inverseJoinColumns = @JoinColumn(name = "choix_id")
    )
    // Set et non List : un choix ne figure qu'une fois, et Hibernate ne met à jour que les choix ajoutés ou retirés
    // (une List sans ordre est un « bag » : toute la collection serait supprimée puis réinsérée)
    private Set<ChoixReponse> choixCoches = new HashSet<>();

    public ReponseQCM() {
        super();
    }

    public Set<ChoixReponse> getChoixCoches() { return choixCoches; }
    public void setChoixCoches(Set<ChoixReponse> choixCoches) { this.choixCoches = new HashSet<>(choixCoches); }
}
