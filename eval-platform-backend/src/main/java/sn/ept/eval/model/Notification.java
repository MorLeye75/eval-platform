package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.TypeNotification;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Fixée par NotificationServiceEnBase avec l'horloge de l'application (Clock), testable. */
    @Column(nullable = false)
    private LocalDateTime dateEnvoi;

    @Column(nullable = false)
    private Boolean lue = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeNotification type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur destinataire;

    /** Évaluation concernée (V12), nulle si la notification n'en a pas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id")
    private Evaluation evaluation;

    /** Copie concernée (V12) : le front ouvre directement ses résultats (DS-13). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soumission_id")
    private Soumission soumission;

    public Notification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }

    public Boolean getLue() { return lue; }
    public void setLue(Boolean lue) { this.lue = lue; }

    public TypeNotification getType() { return type; }
    public void setType(TypeNotification type) { this.type = type; }

    public Utilisateur getDestinataire() { return destinataire; }
    public void setDestinataire(Utilisateur destinataire) { this.destinataire = destinataire; }

    public Evaluation getEvaluation() { return evaluation; }
    public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }

    public Soumission getSoumission() { return soumission; }
    public void setSoumission(Soumission soumission) { this.soumission = soumission; }
}
