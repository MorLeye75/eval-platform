package sn.ept.eval.model;

import jakarta.persistence.*;

@Entity
@Table(name = "reponses_ouvertes")
public class ReponseOuverte extends ReponseEtudiant {

    @Column(columnDefinition = "TEXT")
    private String texteSoumis;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "proposition_ia_id")
    private PropositionIA propositionIA;

    public ReponseOuverte() {
        super();
    }

    public String getTexteSoumis() { return texteSoumis; }
    public void setTexteSoumis(String texteSoumis) { this.texteSoumis = texteSoumis; }

    public PropositionIA getPropositionIA() { return propositionIA; }
    public void setPropositionIA(PropositionIA propositionIA) { this.propositionIA = propositionIA; }
}
