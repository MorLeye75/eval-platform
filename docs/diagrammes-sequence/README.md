# Diagrammes de séquence

Les fichiers `.puml` se lisent avec PlantUML (extension VS Code « PlantUML », ou https://www.plantuml.com/plantuml).
Les fichiers `.md` contiennent des diagrammes Mermaid, affichés directement par GitHub.

| Diagramme | Fichier | Périmètre | État |
|---|---|---|---|
| DS-01 Soumission finale | `DS-01-soumission.puml` | Mor | aligné sur le code |
| DS-02 Sauvegarde automatique | `DS-02-sauvegarde-auto.puml` | Mor | aligné sur le code |
| DS-03 Création et modification d'un brouillon d'évaluation, promotions | `DS-03-creation-evaluation.puml` | Aminou | aligné sur le code |
| DS-04 Publication et dépublication d'une évaluation | `DS-04-publication-evaluation.puml` | Aminou | aligné sur le code |
| DS-05 Passage automatique des statuts (mise à jour atomique, notification d'ouverture) | `DS-05-statuts-automatiques.puml` | Aminou | aligné sur le code |
| DS-06 Aménagement de temps (tiers-temps), plage et verrou de l'évaluation | `DS-06-amenagement-temps.puml` | Aminou | aligné sur le code |
| DS-07 Publication des résultats d'une évaluation | `DS-07-publication-resultats.puml` | Aminou | aligné sur le code |
| DS-08 à DS-09 Correction assistée par IA et validation enseignant, statistiques et export | — | Aminou | réservés |
| DS-10 Liste des épreuves, démarrage et reprise | `DS-10-demarrage.puml` (détaillé), `DS-10-demarrage-simplifie.puml` (présentation) | Mor | aligné sur le code |
| DS-11 Clôture automatique des copies abandonnées | `DS-11-cloture-automatique.puml` | Mor | aligné sur le code |
| DS-12 Correction automatique du code via Judge0 | `DS-12-correction-code-judge0.puml` | Mor | aligné sur le code (Judge0 réel non encore testé) |
| DS-13 Consultation des résultats (détail d'une copie, tableau de bord) | `DS-13-consultation-resultats.puml` | Mor | aligné sur le code |
| DS-14 Connexion, requêtes authentifiées et déconnexion (JWT) | `DS-14-connexion.puml` | Mor | aligné sur le code |
| DS-15 Notifications | `DS-15-notifications.puml` | Mor | brouillon, à valider avec Aminou |
| DS-16 Administration (comptes, import) | — | Aminou | réservé |

Numérotation : DS-03 à DS-09 pour les modules d'Aminou, DS-10 à DS-15 pour ceux de Mor, DS-16 réservé à
l'administration. Les alternatives des diagrammes s'écrivent `alt texte` et non `alt [texte]` : PlantUML ajoute déjà
les crochets.

Chaîne « publication et consultation des résultats » : DS-04 publie l'**évaluation**, DS-01 et DS-11 remettent et
clôturent les copies, DS-12 les corrige, DS-07 publie les **résultats**, DS-15 notifie, DS-13 les affiche à l'étudiant.

Règles communes à tous les diagrammes de passation :

- fin de l'épreuve = `min(dateDebut + durée effective, dateFermeture)`, durée effective = tiers-temps sinon durée
  standard, puis marge de 2 minutes (`TempsEpreuveService`, même calcul partout) ;
- sauvegarde, soumission, reprise, clôture automatique et correction verrouillent la copie (`SELECT … FOR UPDATE`) ;
- erreurs au format RFC 7807, avec un champ `code` pour les 409 (`TEMPS_EXPIRE`, `SOUMISSION_VERROUILLEE`,
  `VERSION_OBSOLETE`).
