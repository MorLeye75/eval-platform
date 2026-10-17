package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.StatutProposition;

@Entity
@Table(name = "propositions_ia")
public class PropositionIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double noteProposee;

    @Column(columnDefinition = "TEXT")
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutProposition statut = StatutProposition.EN_ATTENTE;

    public PropositionIA() {}

    public PropositionIA(Double noteProposee, String justification, StatutProposition statut) {
        this.noteProposee = noteProposee;
        this.justification = justification;
        this.statut = statut;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getNoteProposee() { return noteProposee; }
    public void setNoteProposee(Double noteProposee) { this.noteProposee = noteProposee; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public StatutProposition getStatut() { return statut; }
    public void setStatut(StatutProposition statut) { this.statut = statut; }
}
