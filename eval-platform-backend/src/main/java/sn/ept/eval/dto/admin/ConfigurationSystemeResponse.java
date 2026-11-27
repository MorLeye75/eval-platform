package sn.ept.eval.dto.admin;

import java.util.List;

/**
 * Configuration réellement appliquée par le serveur (DS-18, section D), en lecture seule. Jamais de secret : ni clé de
 * signature, ni jeton Judge0, ni adresse ou mot de passe de base de données.
 */
public record ConfigurationSystemeResponse(List<Section> sections) {

    public record Section(String titre, List<Parametre> parametres) {}

    /** {@code source} : {@code CODE} (valeur fixée dans le code) ou {@code DEPLOIEMENT} (propriété ou variable d'environnement). */
    public record Parametre(String libelle, String valeur, String source) {}
}
