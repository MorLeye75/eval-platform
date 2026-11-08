# Diagrammes de séquence

Les fichiers `.puml` se lisent avec PlantUML (extension VS Code « PlantUML », ou https://www.plantuml.com/plantuml).
Les fichiers `.md` contiennent des diagrammes Mermaid, affichés directement par GitHub.

| Diagramme | Fichier | Périmètre | État |
|---|---|---|---|
| DS-01 Soumission finale | `DS-01-soumission.puml` | Mor | aligné sur le code |
| DS-02 Sauvegarde automatique | `DS-02-sauvegarde-auto.puml` | Mor | aligné sur le code |
| DS-03 Création, publication et aménagement d'une évaluation | `DS-03-creation-evaluation.md` (branche `docs/ds-creation-evaluation`) | Aminou | en revue |
| DS-10 Liste des épreuves, démarrage et reprise | `DS-10-demarrage.puml` (détaillé), `DS-10-demarrage-simplifie.puml` (présentation) | Mor | aligné sur le code |
| DS-11 Clôture automatique des copies abandonnées | `DS-11-cloture-automatique.puml` | Mor | aligné sur le code |
| DS-12 Correction automatique du code via Judge0 | `DS-12-correction-code-judge0.puml` | Mor | aligné sur le code (Judge0 réel non encore testé) |

Numérotation proposée (à confirmer avec Aminou) : DS-03 à DS-09 pour ses modules, DS-10 et suivants pour ceux de Mor.

Règles communes à tous les diagrammes de passation :

- fin de l'épreuve = `min(dateDebut + durée effective, dateFermeture)`, durée effective = tiers-temps sinon durée
  standard, puis marge de 2 minutes (`TempsEpreuveService`, même calcul partout) ;
- sauvegarde, soumission, reprise, clôture automatique et correction verrouillent la copie (`SELECT … FOR UPDATE`) ;
- erreurs au format RFC 7807, avec un champ `code` pour les 409 (`TEMPS_EXPIRE`, `SOUMISSION_VERROUILLEE`,
  `VERSION_OBSOLETE`).
