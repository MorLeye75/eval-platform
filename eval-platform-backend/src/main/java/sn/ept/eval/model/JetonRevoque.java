package sn.ept.eval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Jeton d'une session fermée par la déconnexion : refusé jusqu'à son expiration (DS-14). */
@Entity
@Table(name = "jetons_revoques")
public class JetonRevoque {

    /** Identifiant unique du jeton (claim jti). */
    @Id
    @Column(length = 36)
    private String jti;

    /** Expiration du jeton : au-delà, il serait refusé de toute façon, la ligne peut être purgée. */
    @Column(nullable = false)
    private LocalDateTime expireLe;

    @Column(nullable = false)
    private LocalDateTime revoqueLe;

    protected JetonRevoque() {}

    public JetonRevoque(String jti, LocalDateTime expireLe, LocalDateTime revoqueLe) {
        this.jti = jti;
        this.expireLe = expireLe;
        this.revoqueLe = revoqueLe;
    }

    public String getJti() { return jti; }

    public LocalDateTime getExpireLe() { return expireLe; }

    public LocalDateTime getRevoqueLe() { return revoqueLe; }
}
