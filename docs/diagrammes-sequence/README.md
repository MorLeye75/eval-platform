# Diagrammes de séquence

Les fichiers `.puml` se lisent avec PlantUML (extension VS Code « PlantUML », ou https://www.plantuml.com/plantuml).
Les fichiers `.md` contiennent des diagrammes Mermaid, affichés directement par GitHub.

| Diagramme | Fichier | Périmètre | État |
|---|---|---|---|
| DS-01 Soumission finale | `DS-01-soumission.puml` | Mor | aligné sur le code |
| DS-02 Sauvegarde automatique | `DS-02-sauvegarde-auto.puml` | Mor | aligné sur le code |
| DS-03 Création et modification d'un brouillon d'évaluation | `DS-03-creation-evaluation.puml` | Aminou | aligné sur le code |
| DS-04 Publication et dépublication | `DS-04-publication-evaluation.puml` | Aminou | aligné sur le code |
| DS-05 Passage automatique des statuts | `DS-05-statuts-automatiques.puml` | Aminou | aligné sur le code |
| DS-06 Aménagement de temps (tiers-temps) | `DS-06-amenagement-temps.puml` | Aminou | aligné sur le code |
| DS-10 Liste des épreuves, démarrage et reprise | `DS-10-demarrage.puml` (détaillé), `DS-10-demarrage-simplifie.puml` (présentation) | Mor | aligné sur le code |

Numérotation : DS-03 à DS-09 pour les modules d'Aminou (DS-07 à DS-09 réservés : correction IA, statistiques, administration), DS-10 et suivants pour ceux de Mor.

Règles communes à tous les diagrammes de passation :

- fin de l'épreuve = `min(dateDebut + durée effective, dateFermeture)`, durée effective = tiers-temps sinon durée
  standard, puis marge de 2 minutes (`TempsEpreuveService`, même calcul partout) ;
- sauvegarde, soumission, reprise, clôture automatique et correction verrouillent la copie (`SELECT … FOR UPDATE`) ;
- erreurs au format RFC 7807, avec un champ `code` pour les 409 (`TEMPS_EXPIRE`, `SOUMISSION_VERROUILLEE`,
  `VERSION_OBSOLETE`).
