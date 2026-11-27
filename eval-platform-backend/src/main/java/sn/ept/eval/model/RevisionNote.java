package sn.ept.eval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Trace d'une note revue par l'enseignant avant la publication des résultats : l'ancienne note n'est jamais perdue.
 * Écrite une fois, jamais modifiée.
 */
@Entity
@Table(name = "revisions_note")
public class RevisionNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reponse_id", nullable = false)
    private ReponseEtudiant reponse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auteur_id", nullable = false)
    private Utilisateur auteur;

    @Column(nullable = false)
    private Double ancienneNote;

    @Column(nullable = false)
    private Double nouvelleNote;

    @Column(nullable = false, length = 500)
    private String motif;

    @Column(nullable = false)
    private LocalDateTime reviseeLe;

    protected RevisionNote() {}

    public RevisionNote(ReponseEtudiant reponse, Utilisateur auteur, double ancienneNote, double nouvelleNote,
                        String motif, LocalDateTime reviseeLe) {
        this.reponse = reponse;
        this.auteur = auteur;
        this.ancienneNote = ancienneNote;
        this.nouvelleNote = nouvelleNote;
        this.motif = motif;
        this.reviseeLe = reviseeLe;
    }

    public Long getId() { return id; }
    public ReponseEtudiant getReponse() { return reponse; }
    public Utilisateur getAuteur() { return auteur; }
    public Double getAncienneNote() { return ancienneNote; }
    public Double getNouvelleNote() { return nouvelleNote; }
    public String getMotif() { return motif; }
    public LocalDateTime getReviseeLe() { return reviseeLe; }
}
