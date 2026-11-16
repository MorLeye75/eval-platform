package sn.ept.eval.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.ept.eval.dto.admin.ImportEtudiantsResponse;
import sn.ept.eval.dto.admin.ImportEtudiantsResponse.CompteImporte;
import sn.ept.eval.dto.admin.ImportEtudiantsResponse.LigneErreur;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Import CSV d'étudiants (DS-16) : tout ou rien. Toutes les lignes sont vérifiées avant la moindre création ; une seule
 * erreur et rien n'est importé. Colonnes : nom, prenom, email, numEtudiant, promotion (séparateur « ; » ou « , »).
 */
@Service
public class ImportEtudiantsService {

    public static final int LIGNES_MAX = 500;
    static final List<String> COLONNES = List.of("nom", "prenom", "email", "numetudiant", "promotion");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UtilisateurRepository utilisateurRepository;
    private final EtudiantRepository etudiantRepository;
    private final PromotionRepository promotionRepository;
    private final PasswordEncoder passwordEncoder;
    private final GenerateurMotDePasse generateur;

    public ImportEtudiantsService(UtilisateurRepository utilisateurRepository, EtudiantRepository etudiantRepository,
                                  PromotionRepository promotionRepository, PasswordEncoder passwordEncoder,
                                  GenerateurMotDePasse generateur) {
        this.utilisateurRepository = utilisateurRepository;
        this.etudiantRepository = etudiantRepository;
        this.promotionRepository = promotionRepository;
        this.passwordEncoder = passwordEncoder;
        this.generateur = generateur;
    }

    private record Ligne(int numero, String nom, String prenom, String email, String numEtudiant, Promotion promotion) {}

    public ImportEtudiantsResponse importer(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
        String contenu;
        try {
            contenu = new String(fichier.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Le fichier n'a pas pu être lu.");
        }
        return importer(contenu);
    }

    @Transactional
    public ImportEtudiantsResponse importer(String contenu) {
        List<String> lignesBrutes = contenu.replace("﻿", "").lines().filter(l -> !l.isBlank()).toList();
        if (lignesBrutes.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
        if (lignesBrutes.size() - 1 > LIGNES_MAX) {
            throw new IllegalArgumentException("Un import est limité à " + LIGNES_MAX + " étudiants par fichier.");
        }
        char separateur = nombre(lignesBrutes.get(0), ';') >= nombre(lignesBrutes.get(0), ',') ? ';' : ',';
        List<String> entete = decouper(lignesBrutes.get(0), separateur).stream()
                .map(c -> c.trim().toLowerCase(Locale.ROOT)).toList();
        if (!entete.equals(COLONNES)) {
            throw new IllegalArgumentException("Colonnes attendues, dans cet ordre : nom;prenom;email;numEtudiant;promotion.");
        }
        if (lignesBrutes.size() == 1) {
            throw new IllegalArgumentException("Le fichier ne contient aucun étudiant.");
        }

        Map<String, List<Promotion>> promotions = promotionRepository.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getNom().trim().toLowerCase(Locale.ROOT)));
        List<LigneErreur> erreurs = new ArrayList<>();
        List<Ligne> valides = new ArrayList<>();
        Set<String> emailsVus = new HashSet<>();
        Set<String> numerosVus = new HashSet<>();

        for (int i = 1; i < lignesBrutes.size(); i++) {
            int numero = i + 1; // l'en-tête est la ligne 1 (les lignes vides sont ignorées, la numérotation reste approchée)
            List<String> champs = decouper(lignesBrutes.get(i), separateur);
            if (champs.size() != COLONNES.size()) {
                erreurs.add(new LigneErreur(numero, "5 colonnes attendues, " + champs.size() + " trouvée(s)."));
                continue;
            }
            String nom = champs.get(0).trim();
            String prenom = champs.get(1).trim();
            String email = Utilisateur.normaliserEmail(champs.get(2));
            String num = champs.get(3).trim();
            String nomPromotion = champs.get(4).trim();
            String probleme = null;
            Promotion promotion = null;
            if (nom.isEmpty() || prenom.isEmpty() || email.isEmpty() || num.isEmpty() || nomPromotion.isEmpty()) {
                probleme = "Les 5 champs sont obligatoires.";
            } else if (!EMAIL.matcher(email).matches() || email.length() > 255) {
                probleme = "E-mail invalide : " + email;
            } else if (!emailsVus.add(email)) {
                probleme = "E-mail en double dans le fichier : " + email;
            } else if (!numerosVus.add(num)) {
                probleme = "Numéro d'étudiant en double dans le fichier : " + num;
            } else {
                List<Promotion> trouvees = promotions.getOrDefault(nomPromotion.toLowerCase(Locale.ROOT), List.of());
                if (trouvees.isEmpty()) probleme = "Promotion inconnue : " + nomPromotion;
                else if (trouvees.size() > 1) probleme = "Promotion ambiguë (plusieurs années) : " + nomPromotion;
                else promotion = trouvees.get(0);
            }
            if (probleme != null) erreurs.add(new LigneErreur(numero, probleme));
            else valides.add(new Ligne(numero, nom, prenom, email, num, promotion));
        }

        // Doublons avec la base : une requête pour tout le fichier
        if (!valides.isEmpty()) {
            Set<String> emailsPris = new HashSet<>(utilisateurRepository.emailsExistants(
                    valides.stream().map(Ligne::email).toList()));
            Set<String> numerosPris = new HashSet<>(etudiantRepository.numerosExistants(
                    valides.stream().map(Ligne::numEtudiant).toList()));
            for (Ligne l : valides) {
                if (emailsPris.contains(l.email())) erreurs.add(new LigneErreur(l.numero(), "E-mail déjà utilisé : " + l.email()));
                else if (numerosPris.contains(l.numEtudiant()))
                    erreurs.add(new LigneErreur(l.numero(), "Numéro d'étudiant déjà utilisé : " + l.numEtudiant()));
            }
        }
        if (!erreurs.isEmpty()) {
            erreurs.sort(java.util.Comparator.comparingInt(LigneErreur::ligne));
            return new ImportEtudiantsResponse(false, 0, List.copyOf(erreurs), List.of());
        }

        // Mots de passe provisoires, hachés en parallèle (BCrypt est lent : 500 comptes en série prendraient une minute)
        Map<Integer, String> motsDePasse = new HashMap<>();
        valides.forEach(l -> motsDePasse.put(l.numero(), generateur.generer()));
        Map<Integer, String> haches = valides.parallelStream().collect(Collectors.toConcurrentMap(
                Ligne::numero, l -> passwordEncoder.encode(motsDePasse.get(l.numero()))));

        List<Etudiant> etudiants = valides.stream()
                .map(l -> new Etudiant(l.nom(), l.prenom(), l.email(), haches.get(l.numero()), l.numEtudiant(), l.promotion()))
                .toList();
        etudiantRepository.saveAllAndFlush(etudiants);

        List<CompteImporte> comptes = valides.stream()
                .map(l -> new CompteImporte(l.numero(), l.email(), motsDePasse.get(l.numero()))).toList();
        return new ImportEtudiantsResponse(true, comptes.size(), List.of(), comptes);
    }

    private static int nombre(String texte, char c) {
        return (int) texte.chars().filter(x -> x == c).count();
    }

    /** Découpe une ligne CSV : guillemets doubles pris en charge (séparateur ou guillemet dans un champ). */
    static List<String> decouper(String ligne, char separateur) {
        List<String> champs = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        boolean entreGuillemets = false;
        for (int i = 0; i < ligne.length(); i++) {
            char c = ligne.charAt(i);
            if (entreGuillemets) {
                if (c == '"' && i + 1 < ligne.length() && ligne.charAt(i + 1) == '"') {
                    courant.append('"');
                    i++;
                } else if (c == '"') {
                    entreGuillemets = false;
                } else {
                    courant.append(c);
                }
            } else if (c == '"') {
                entreGuillemets = true;
            } else if (c == separateur) {
                champs.add(courant.toString());
                courant.setLength(0);
            } else {
                courant.append(c);
            }
        }
        champs.add(courant.toString());
        return champs;
    }
}
