package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.admin.CompteCreeResponse;
import sn.ept.eval.dto.admin.CreerCompteRequest;
import sn.ept.eval.dto.admin.CreerPromotionRequest;
import sn.ept.eval.dto.admin.ModifierCompteRequest;
import sn.ept.eval.dto.admin.MotDePasseProvisoireResponse;
import sn.ept.eval.dto.admin.UtilisateurAdminResponse;
import sn.ept.eval.dto.evaluation.PromotionResponse;
import sn.ept.eval.exception.OperationCompteRefuseeException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.Administrateur;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.model.enums.Role;
import sn.ept.eval.repository.AdministrateurRepository;
import sn.ept.eval.repository.EnseignantRepository;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Gestion des comptes par l'administrateur (DS-16). Un compte n'est jamais supprimé : il est désactivé, et
 * {@code AuthTokenFilter} refuse ses jetons dès la requête suivante. Les mots de passe provisoires sont générés par le
 * serveur, renvoyés une seule fois et stockés hachés.
 */
@Service
@Transactional
public class AdminComptesService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UtilisateurRepository utilisateurRepository;
    private final EtudiantRepository etudiantRepository;
    private final EnseignantRepository enseignantRepository;
    private final AdministrateurRepository administrateurRepository;
    private final PromotionRepository promotionRepository;
    private final SoumissionRepository soumissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final java.time.Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final GenerateurMotDePasse generateur;

    public AdminComptesService(UtilisateurRepository utilisateurRepository, EtudiantRepository etudiantRepository,
                               EnseignantRepository enseignantRepository, AdministrateurRepository administrateurRepository,
                               PromotionRepository promotionRepository, SoumissionRepository soumissionRepository,
                               EvaluationRepository evaluationRepository, java.time.Clock clock,
                               PasswordEncoder passwordEncoder, GenerateurMotDePasse generateur) {
        this.evaluationRepository = evaluationRepository;
        this.clock = clock;
        this.utilisateurRepository = utilisateurRepository;
        this.etudiantRepository = etudiantRepository;
        this.enseignantRepository = enseignantRepository;
        this.administrateurRepository = administrateurRepository;
        this.promotionRepository = promotionRepository;
        this.soumissionRepository = soumissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.generateur = generateur;
    }

    // ------------------------------------------------------------------ A — lister

    @Transactional(readOnly = true)
    public List<UtilisateurAdminResponse> lister(Role role, Boolean actif, String recherche) {
        String q = recherche == null || recherche.isBlank() ? null : recherche.trim().toLowerCase(Locale.ROOT);
        return utilisateurRepository.findAll().stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> actif == null || Boolean.TRUE.equals(u.getActif()) == actif)
                .filter(u -> q == null || contient(u.getNom(), q) || contient(u.getPrenom(), q) || contient(u.getEmail(), q))
                .sorted(Comparator.comparing((Utilisateur u) -> u.getNom().toLowerCase(Locale.ROOT))
                        .thenComparing(u -> u.getPrenom().toLowerCase(Locale.ROOT)))
                .map(this::versResponse)
                .toList();
    }

    private static boolean contient(String texte, String q) {
        return texte != null && texte.toLowerCase(Locale.ROOT).contains(q);
    }

    public PromotionResponse creerPromotion(CreerPromotionRequest requete) {
        String nom = requete.nom().trim();
        String annee = requete.annee().trim();
        if (nom.isEmpty() || annee.isEmpty()) {
            throw new IllegalArgumentException("Le nom et l'année de la promotion sont obligatoires.");
        }
        if (promotionRepository.existsByNomIgnoreCaseAndAnnee(nom, annee)) {
            throw new OperationCompteRefuseeException("Une promotion « " + nom + " » existe déjà pour l'année " + annee + ".");
        }
        Promotion promotion = promotionRepository.save(new Promotion(nom, annee));
        return new PromotionResponse(promotion.getId(), promotion.getNom(), promotion.getAnnee(), 0);
    }

    /** Renomme une promotion ; les étudiants et les évaluations y restent rattachés (le lien est l'identifiant). */
    public PromotionResponse modifierPromotion(Long id, CreerPromotionRequest requete) {
        Promotion promotion = promotion(id);
        String nom = requete.nom().trim();
        String annee = requete.annee().trim();
        if (nom.isEmpty() || annee.isEmpty()) {
            throw new IllegalArgumentException("Le nom et l'année de la promotion sont obligatoires.");
        }
        if (promotionRepository.existsByNomIgnoreCaseAndAnneeAndIdNot(nom, annee, id)) {
            throw new OperationCompteRefuseeException("Une promotion « " + nom + " » existe déjà pour l'année " + annee + ".");
        }
        promotion.setNom(nom);
        promotion.setAnnee(annee);
        Promotion enregistree = promotionRepository.saveAndFlush(promotion);
        return new PromotionResponse(enregistree.getId(), enregistree.getNom(), enregistree.getAnnee(),
                etudiantRepository.countByPromotionId(id));
    }

    /** Supprime une promotion vide : sans étudiant ni évaluation, rien d'existant ne perd son rattachement. */
    public void supprimerPromotion(Long id) {
        Promotion promotion = promotion(id);
        long etudiants = etudiantRepository.countByPromotionId(id);
        if (etudiants > 0) {
            throw new OperationCompteRefuseeException("Cette promotion compte " + etudiants + " étudiant(s) : elle ne peut pas être supprimée.");
        }
        long evaluations = evaluationRepository.countByPromotionsId(id);
        if (evaluations > 0) {
            throw new OperationCompteRefuseeException("Cette promotion est liée à " + evaluations + " évaluation(s) : elle ne peut pas être supprimée.");
        }
        promotionRepository.delete(promotion);
        promotionRepository.flush();
    }

    // ------------------------------------------------------------------ B — créer

    public CompteCreeResponse creer(CreerCompteRequest requete) {
        String email = emailValide(requete.email());
        String nom = requete.nom().trim();
        String prenom = requete.prenom().trim();
        if (nom.isEmpty() || prenom.isEmpty()) {
            throw new IllegalArgumentException("Le nom et le prénom sont obligatoires.");
        }
        if (utilisateurRepository.existsByEmail(email)) {
            throw new OperationCompteRefuseeException("Cet e-mail est déjà utilisé par un autre compte.");
        }
        String motDePasse = generateur.generer();
        String hache = passwordEncoder.encode(motDePasse);

        Utilisateur compte = switch (requete.role()) {
            case ETUDIANT -> {
                String numero = nettoyer(requete.numEtudiant());
                if (numero == null) throw new IllegalArgumentException("Le numéro d'étudiant est obligatoire.");
                if (requete.promotionId() == null) throw new IllegalArgumentException("La promotion est obligatoire pour un étudiant.");
                verifierNumeroLibre(numero, null);
                yield new Etudiant(nom, prenom, email, hache, numero, promotion(requete.promotionId()));
            }
            case ENSEIGNANT -> {
                String matricule = nettoyer(requete.matricule());
                verifierMatriculeLibre(matricule, null);
                yield new Enseignant(nom, prenom, email, hache, matricule);
            }
            case ADMIN -> new Administrateur(nom, prenom, email, hache);
        };
        compte.setMotDePasseProvisoire(true);
        return new CompteCreeResponse(versResponse(utilisateurRepository.saveAndFlush(compte)), motDePasse);
    }

    // ------------------------------------------------------------------ C — modifier

    public UtilisateurAdminResponse modifier(Long id, ModifierCompteRequest requete) {
        Utilisateur compte = compte(id);
        String email = emailValide(requete.email());
        String nom = requete.nom().trim();
        String prenom = requete.prenom().trim();
        if (nom.isEmpty() || prenom.isEmpty()) {
            throw new IllegalArgumentException("Le nom et le prénom sont obligatoires.");
        }
        utilisateurRepository.findByEmail(email).filter(autre -> !autre.getId().equals(id)).ifPresent(autre -> {
            throw new OperationCompteRefuseeException("Cet e-mail est déjà utilisé par un autre compte.");
        });

        Object reel = Hibernate.unproxy(compte);
        if (reel instanceof Etudiant etudiant) {
            String numero = nettoyer(requete.numEtudiant());
            if (numero == null) throw new IllegalArgumentException("Le numéro d'étudiant est obligatoire.");
            if (requete.promotionId() == null) throw new IllegalArgumentException("La promotion est obligatoire pour un étudiant.");
            verifierNumeroLibre(numero, etudiant);
            Promotion nouvelle = promotion(requete.promotionId());
            boolean change = etudiant.getPromotion() == null || !Objects.equals(etudiant.getPromotion().getId(), nouvelle.getId());
            if (change && soumissionRepository.existsByEtudiantId(etudiant.getId())) {
                throw new OperationCompteRefuseeException(
                        "Cet étudiant a déjà des copies : sa promotion ne peut plus changer.");
            }
            etudiant.setNumEtudiant(numero);
            etudiant.setPromotion(nouvelle);
        } else if (reel instanceof Enseignant enseignant) {
            String matricule = nettoyer(requete.matricule());
            verifierMatriculeLibre(matricule, enseignant);
            enseignant.setMatricule(matricule);
        }
        compte.setNom(nom);
        compte.setPrenom(prenom);
        compte.setEmail(email);
        return versResponse(utilisateurRepository.saveAndFlush(compte));
    }

    // ------------------------------------------------------------------ D — désactiver / réactiver

    public UtilisateurAdminResponse changerEtat(Long id, boolean actif, String emailAdminConnecte) {
        Utilisateur compte = compte(id);
        if (!actif) {
            if (compte.getEmail().equals(Utilisateur.normaliserEmail(emailAdminConnecte))) {
                throw new OperationCompteRefuseeException("Vous ne pouvez pas désactiver votre propre compte.");
            }
            if (Hibernate.unproxy(compte) instanceof Administrateur && Boolean.TRUE.equals(compte.getActif())) {
                // Verrou sur les administrateurs actifs : deux désactivations simultanées sont sérialisées
                long actifs = administrateurRepository.findActifsForUpdate().size();
                if (actifs <= 1) {
                    throw new OperationCompteRefuseeException("Il doit rester au moins un administrateur actif.");
                }
            }
        }
        compte.setActif(actif);
        return versResponse(utilisateurRepository.saveAndFlush(compte));
    }

    // ------------------------------------------------------------------ E — mot de passe provisoire

    public MotDePasseProvisoireResponse reinitialiserMotDePasse(Long id) {
        Utilisateur compte = compte(id);
        String motDePasse = generateur.generer();
        compte.setMotDePasse(passwordEncoder.encode(motDePasse));
        compte.setMotDePasseModifieLe(java.time.LocalDateTime.now(clock));
        compte.setMotDePasseProvisoire(true);
        utilisateurRepository.save(compte);
        return new MotDePasseProvisoireResponse(motDePasse);
    }

    // ------------------------------------------------------------------ communs

    private Utilisateur compte(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceNonTrouveeException("Compte introuvable (ID: " + id + ")"));
    }

    private Promotion promotion(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new RessourceNonTrouveeException("Promotion introuvable (ID: " + id + ")"));
    }

    private static String emailValide(String brut) {
        String email = Utilisateur.normaliserEmail(brut);
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("L'e-mail n'est pas valide.");
        }
        return email;
    }

    /** Texte vide = absent : les colonnes uniques acceptent plusieurs NULL, mais pas plusieurs chaînes vides. */
    static String nettoyer(String valeur) {
        if (valeur == null) return null;
        String propre = valeur.trim();
        return propre.isEmpty() ? null : propre;
    }

    private void verifierNumeroLibre(String numero, Etudiant propre) {
        etudiantRepository.findByNumEtudiant(numero).filter(autre -> propre == null || !autre.getId().equals(propre.getId()))
                .ifPresent(autre -> {
                    throw new OperationCompteRefuseeException("Ce numéro d'étudiant est déjà utilisé.");
                });
    }

    private void verifierMatriculeLibre(String matricule, Enseignant propre) {
        if (matricule == null) return;
        enseignantRepository.findByMatricule(matricule).filter(autre -> propre == null || !autre.getId().equals(propre.getId()))
                .ifPresent(autre -> {
                    throw new OperationCompteRefuseeException("Ce matricule est déjà utilisé.");
                });
    }

    private UtilisateurAdminResponse versResponse(Utilisateur u) {
        Object reel = Hibernate.unproxy(u);
        Long promotionId = null;
        String promotion = null;
        String numEtudiant = null;
        String matricule = null;
        if (reel instanceof Etudiant e) {
            numEtudiant = e.getNumEtudiant();
            if (e.getPromotion() != null) {
                promotionId = e.getPromotion().getId();
                promotion = e.getPromotion().getNom();
            }
        } else if (reel instanceof Enseignant e) {
            matricule = e.getMatricule();
        }
        return new UtilisateurAdminResponse(u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getRole(),
                Boolean.TRUE.equals(u.getActif()), u.getDateCreation(), u.getDerniereConnexion(),
                promotionId, promotion, numEtudiant, matricule);
    }
}
