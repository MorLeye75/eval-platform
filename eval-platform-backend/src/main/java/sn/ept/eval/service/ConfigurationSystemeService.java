package sn.ept.eval.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import sn.ept.eval.dto.admin.ConfigurationSystemeResponse;
import sn.ept.eval.dto.admin.ConfigurationSystemeResponse.Parametre;
import sn.ept.eval.dto.admin.ConfigurationSystemeResponse.Section;

import java.util.List;

/**
 * Configuration en vigueur (DS-18, section D) : on affiche ce que le serveur applique réellement, lu aux mêmes
 * endroits que les services qui l'appliquent, avec les mêmes valeurs par défaut. Chaque ligne dit d'où vient la valeur.
 * Aucun secret n'est exposé : seules des clés choisies une à une sont lues, aucune n'est une clé, un jeton ou une URL.
 */
@Service
public class ConfigurationSystemeService {

    static final String CODE = "CODE";
    static final String DEPLOIEMENT = "DEPLOIEMENT";

    private final Environment environnement;

    public ConfigurationSystemeService(Environment environnement) {
        this.environnement = environnement;
    }

    public ConfigurationSystemeResponse configuration() {
        return new ConfigurationSystemeResponse(List.of(
                new Section("Passation", List.of(
                        new Parametre("Marge de tolérance après la fermeture", TempsEpreuveService.MARGE_TOLERANCE_MINUTES + " min", CODE),
                        new Parametre("Tâches de fond (clôture des copies, statuts, publications programmées)",
                                environnement.getProperty("passation.taches-planifiees.actif", "true").equals("true")
                                        ? "actives, toutes les " + (Long.parseLong(environnement.getProperty("passation.taches-planifiees.intervalle-ms", "60000")) / 1000) + " s"
                                        : "désactivées", DEPLOIEMENT))),
                new Section("Notation", List.of(
                        new Parametre("Notes des statistiques", "ramenées sur 20 (note / barème × 20)", CODE),
                        new Parametre("Seuil de réussite", (int) StatistiquesEvaluationService.SEUIL_REUSSITE_SUR_20 + " / 20", CODE),
                        new Parametre("Évaluations comparables à la fois",
                                ComparaisonEvaluationsService.MIN + " à " + ComparaisonEvaluationsService.MAX, CODE))),
                new Section("Sécurité des comptes", List.of(
                        new Parametre("Durée de validité d'une session", dureeSession(), DEPLOIEMENT),
                        new Parametre("Échecs de connexion avant blocage", environnement.getProperty("securite.login.max-echecs", "5"), DEPLOIEMENT),
                        new Parametre("Fenêtre de comptage des échecs", environnement.getProperty("securite.login.fenetre-minutes", "15") + " min", DEPLOIEMENT),
                        new Parametre("Durée du blocage", environnement.getProperty("securite.login.duree-blocage-minutes", "15") + " min", DEPLOIEMENT),
                        new Parametre("Mot de passe attribué par un administrateur", "provisoire : à changer à la première connexion", CODE))),
                new Section("Correction", List.of(
                        new Parametre("Correction des questions ouvertes", libelleFournisseurIA(), DEPLOIEMENT),
                        new Parametre("Décision finale", "toujours l'enseignant : l'IA propose, elle ne note jamais seule", CODE),
                        new Parametre("Exécutions de code simultanées (Judge0)", environnement.getProperty("correction.judge0.concurrence", "2"), DEPLOIEMENT))),
                new Section("Administration", List.of(
                        new Parametre("Import CSV d'étudiants", ImportEtudiantsService.LIGNES_MAX + " lignes au plus, tout ou rien", CODE),
                        new Parametre("Création des comptes", "par un administrateur uniquement (pas d'inscription libre)", CODE)))));
    }

    private String dureeSession() {
        long minutes = Long.parseLong(environnement.getProperty("jwt.expiration-ms", "86400000")) / 60_000;
        return minutes % 60 == 0 ? (minutes / 60) + " h" : minutes + " min";
    }

    private String libelleFournisseurIA() {
        String fournisseur = environnement.getProperty("correction.ia.fournisseur", "simule");
        return "simule".equals(fournisseur) ? "correcteur simulé (démonstration, pas un vrai modèle)" : "modèle « " + fournisseur + " »";
    }
}
