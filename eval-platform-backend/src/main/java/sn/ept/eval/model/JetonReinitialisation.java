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
 * Lien de réinitialisation du mot de passe envoyé par e-mail. Seule l'empreinte SHA-256 du jeton est stockée ;
 * usage unique, expiration courte.
 */
@Entity
@Table(name = "jetons_reinitialisation")
public class JetonReinitialisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** SHA-256 du jeton, en hexadécimal. */
    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    @Column(nullable = false)
    private LocalDateTime creeLe;

    @Column(nullable = false)
    private LocalDateTime expireLe;

    /** Posée à l'utilisation, ou quand une demande plus récente remplace ce lien. */
    private LocalDateTime utiliseLe;

    protected JetonReinitialisation() {}

    public JetonReinitialisation(Utilisateur utilisateur, String empreinte, LocalDateTime creeLe, LocalDateTime expireLe) {
        this.utilisateur = utilisateur;
        this.empreinte = empreinte;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
    }

    public boolean estUtilisable(LocalDateTime maintenant) {
        return utiliseLe == null && maintenant.isBefore(expireLe);
    }

    public Long getId() { return id; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public String getEmpreinte() { return empreinte; }
    public LocalDateTime getCreeLe() { return creeLe; }
    public LocalDateTime getExpireLe() { return expireLe; }
    public LocalDateTime getUtiliseLe() { return utiliseLe; }
    public void setUtiliseLe(LocalDateTime utiliseLe) { this.utiliseLe = utiliseLe; }
}
