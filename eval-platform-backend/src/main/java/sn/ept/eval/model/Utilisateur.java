package sn.ept.eval.model;

import jakarta.persistence.*;
import sn.ept.eval.model.enums.Role;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "utilisateurs")
@Inheritance(strategy = InheritanceType.JOINED)
public class Utilisateur extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Boolean actif = true;

    private LocalDateTime derniereConnexion;

    @OneToMany(mappedBy = "destinataire", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Notification> notifications = new java.util.ArrayList<>();

    public Utilisateur() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    /** Enregistré en minuscules et sans espaces autour : la connexion et l'unicité ne dépendent pas de la casse. */
    public void setEmail(String email) { this.email = normaliserEmail(email); }

    /**
     * Forme unique d'un email (minuscules, sans espaces autour), pour l'enregistrer et pour le rechercher. Un
     * téléphone qui met une majuscule au début ne doit pas faire échouer la connexion.
     */
    public static String normaliserEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Boolean getActif() { return actif; }
    public void setActif(Boolean actif) { this.actif = actif; }


    public LocalDateTime getDerniereConnexion() { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime derniereConnexion) { this.derniereConnexion = derniereConnexion; }

    public java.util.List<Notification> getNotifications() { return notifications; }
    public void setNotifications(java.util.List<Notification> notifications) { this.notifications = notifications; }
}
