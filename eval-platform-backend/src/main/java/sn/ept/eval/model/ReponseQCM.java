package sn.ept.eval.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reponses_qcm")
public class ReponseQCM extends ReponseEtudiant {

    @ManyToMany
    @JoinTable(
        name = "reponse_qcm_choix",
        joinColumns = @JoinColumn(name = "reponse_qcm_id"),
        inverseJoinColumns = @JoinColumn(name = "choix_id")
    )
    private List<ChoixReponse> choixCoches = new ArrayList<>();

    public ReponseQCM() {
        super();
    }

    public List<ChoixReponse> getChoixCoches() { return choixCoches; }
    public void setChoixCoches(List<ChoixReponse> choixCoches) { this.choixCoches = choixCoches; }
}
