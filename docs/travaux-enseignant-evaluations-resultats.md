# Module enseignant — création, publication et résultats des évaluations

Branche `feature/enseignant-resultats-notifications` (fusion avec `feature/passation-ds01-ds02` de Mor). Périmètre :
Aminou (espace enseignant). Ce document résume ce qui est fait, comment le vérifier, les décisions prises et ce qui reste.

## En bref

- Un enseignant crée, modifie, publie, dépublie et supprime ses évaluations **sur l'API**, sans donnée simulée (DS-03, DS-04).
- Le passage `PUBLIEE → EN_COURS → TERMINEE` est automatique et atomique (DS-05).
- Le tiers-temps est borné par la plage d'ouverture et verrouillé une fois la copie commencée (DS-06).
- L'enseignant **publie les résultats** d'une évaluation terminée : c'est ce qui fait passer la copie de l'étudiant de
  « en attente » à « publiés » (DS-07, puis DS-13 côté étudiant).
- Les branches de Mor et d'Aminou sont fusionnées. Tests : **267 côté backend** (PostgreSQL réel via Docker compris) et
  **197 côté frontend**, tous verts (voir « Vérifier »).

## Diagrammes (PlantUML, dans `docs/diagrammes-sequence`)

| Diagramme | Contenu |
|---|---|
| `DS-03-creation-evaluation.puml` | promotions, création, modification et suppression d'un brouillon, règles des questions et des cas de test |
| `DS-04-publication-evaluation.puml` | publication (dont la plage d'ouverture) et dépublication |
| `DS-05-statuts-automatiques.puml` | passage de statut par `UPDATE … WHERE statut = ancien`, notification d'ouverture sans doublon |
| `DS-06-amenagement-temps.puml` | tiers-temps : plage, verrou de l'évaluation, copie déjà commencée |
| `DS-07-publication-resultats.puml` | publication des résultats, copies pas encore notées |

Chaîne « publication et consultation des résultats » : DS-04 publie l'**évaluation**, DS-01 et DS-11 remettent et clôturent
les copies, DS-12 les corrige, **DS-07 publie les résultats**, DS-15 notifie, DS-13 les affiche à l'étudiant.

## Ce qui a été fait

### Backend

| Sujet | Détail |
|---|---|
| Création et modification | `POST` / `PUT /enseignant/evaluations`, brouillon d'abord ; règles par type de question ; modifiable uniquement en brouillon (409 `EVALUATION_NON_MODIFIABLE`) |
| Promotions | `GET /promotions` (enseignant, admin) : l'assistant n'avait aucun moyen de choisir les destinataires |
| Liste et détail | `nbCopies` et `nbAttendus` calculés en une requête groupée (pas de requête par évaluation) |
| Publication | exige questions, fermeture future et **plage d'ouverture ≥ durée maximale (tiers-temps compris)** |
| Dépublication | `PUBLIEE → BROUILLON` tant qu'aucune copie n'existe ; l'évaluation est verrouillée (`FOR UPDATE`) |
| Tiers-temps | durée ≥ durée standard, **≤ plage d'ouverture**, étudiant de la promotion, **verrou de l'évaluation**, refusé dès que la copie existe |
| Concurrence | `@Version` sur `Evaluation` (migration **V9**) : un « modifier » et un « publier » simultanés ne s'écrasent pas (409 `CONFLIT_CONCURRENT`) |
| Statuts automatiques | mise à jour conditionnelle `versioned`, une transaction par évaluation, `PassationScheduler` dans son propre `try/catch` |
| Publication des résultats | `POST /enseignant/evaluations/{id}/resultats/publier` : évaluation `TERMINEE` et marge de 2 min écoulée ; bascule atomique ; bilan `nbCopiesNotees` / `nbCopiesEnAttente` |
| Cas de test | **entrée facultative**, **sortie attendue obligatoire et non vide** (Judge0 traite une sortie vide comme absente) |
| Détail étudiant | `GET /etudiant/evaluations/{id}` (demandé par Mor pour l'écran d'accueil du quiz) |
| Notifications | interface `NotificationService` (3 événements) + implémentation par défaut sans effet, remplacée dès que le module de Mor est branché |

### Frontend

| Écran | Avant | Maintenant |
|---|---|---|
| Assistant de création / modification | mocks, bouton « Publier » désactivé | API ; choix des promotions ; erreurs du serveur affichées ; brouillon local effacé seulement après confirmation |
| Modification d'une évaluation | questions perdues (mock) | détail chargé depuis l'API ; message si l'évaluation n'est plus un brouillon |
| Mes évaluations | mocks, boutons sans effet | API ; publier, repasser en brouillon, supprimer, publier les résultats ; actions selon l'état |
| Publication des résultats | notes d'étudiants inventées, « publication » simulée | évaluations terminées de l'API, vraie route, bilan des copies notifiées |
| Formulaire des cas de test | — | rappel « programme complet : stdin → stdout », entrée facultative |

## Décisions prises

| Décision | Raison |
|---|---|
| Un brouillon n'exige pas « ouverture + durée ≤ fermeture » | les dates se règlent souvent en dernier ; la règle est vérifiée à la publication et au tiers-temps, et signalée dès l'étape 1 quand on publie immédiatement |
| Tiers-temps refusé (400) plutôt que simple avertissement | la fin d'une épreuve est plafonnée à la fermeture : un tiers-temps plus long serait sans effet, donc trompeur |
| Résultats publiables seulement après fermeture + 2 min | même règle que DS-13 pour dévoiler les corrigés : plus personne ne compose |
| Copies pas encore notées : publication autorisée | elles restent « en attente » ; leur étudiant est notifié à la notation (`resultatPublie`) |
| Notifications créées dans la transaction de l'événement | jamais « publié sans notification », jamais de notification pour un événement annulé |
| `NotificationService` en interface + bean par défaut | pas de conflit avec le module de Mor : son bean remplace le mien sans modifier mon code |
| `window.confirm` pour supprimer et publier les résultats | actions destructives ou irréversibles ; un vrai dialogue pourra le remplacer |

## Vérifier

```bash
# Backend (Docker lancé pour les tests PostgreSQL)
cd eval-platform-backend
mvn clean test          # « clean » : une migration renommée resterait sinon dans target/classes

# Frontend
cd eval-platform-frontend
npm install && npx tsc -b && npm run lint && npm test
```

Tests ajoutés pour ce module :

- **Backend** : `EvaluationServiceTest`, `AmenagementTempsServiceTest`, `EvaluationStatutServiceTest`,
  `PublicationResultatsServiceTest`, `PromotionServiceTest`, `EvaluationDtoValidationTest`, `EvaluationEtudiantDetailTest`
  (unitaires) ; `EvaluationControllerIntegrationTest`, `EvaluationStatutIntegrationTest` (deux passages simultanés, une
  seule notification), `PublicationResultatsIntegrationTest` (de « en attente » à « publiés ») sur H2 ;
  `EnseignantPostgresIntegrationTest` sur PostgreSQL (Testcontainers) : mises à jour conditionnelles, publication des
  résultats atomique entre 6 appels simultanés, et l'enregistrement d'un tiers-temps qui attend le verrou partagé pris
  par un démarrage de copie.
- **Frontend** : `evaluationsEnseignant.test.ts`, `apiBrouillon.test.ts`, `brouillon.test.ts`, `infosGenerales.test.tsx`,
  `enregistrement.test.tsx`, `ListeEvaluationsEnseignant.test.tsx`, `PublicationResultats.test.tsx`.
- **Parcours réel** : le cycle création → publication → dépublication → suppression a été déroulé par l'API sur un
  PostgreSQL 18 local (19 vérifications), avant la fusion.

Postman : dossier « 05 - Évaluations enseignant (DS-03 à DS-07) » dans `docs/Plateforme_Evaluation_Postman_Collection.json`.

## Points d'attention

- **Base locale existante** : la migration d'Aminou s'appelait `V3__evaluation_version.sql` et est devenue `V9`. Une base
  déjà migrée avec l'ancienne V3 refuse de démarrer (checksum). Réinitialiser la base locale, ou `flyway repair` puis
  rejouer la migration à la main.
- **Mockito sous Windows** : « Could not self-attach to current VM » → `JAVA_TOOL_OPTIONS=-Djdk.attach.allowAttachSelf=true`.
- **`npm ci`** échoue sous Windows (le `package-lock.json` ne contient pas un binaire optionnel) : utiliser `npm install`,
  puis ne pas committer le lockfile modifié.

## Reste à faire

**Module d'Aminou**

- **DS-08 Correction assistée par IA et validation enseignant** : connecteur LLM (ou simulé), `PropositionIA`, validation et
  ajustement de la note. Dépend de la décision sur `CORRECTION_VALIDEE` (voir questions à Mor). À la validation de la dernière
  note, appeler `NotificationService.resultatPublie(copie)` si les résultats sont déjà publiés.
- **DS-09 Statistiques et export** : `StatistiquesEvaluation` (moyenne, min, max, écart-type, taux de réussite), export CSV.
  Le détail des notes par étudiant de l'écran de publication y sera rattaché.
- **DS-16 Administration** : CRUD des comptes, import CSV d'étudiants.
- Écrans encore simulés : tableau de bord enseignant, supervision en temps réel, statistiques, validation IA, export.
- Gestion du tiers-temps dans l'interface (la route existe, DS-06, aucun écran).
- Dupliquer une évaluation (bouton retiré faute de route).

**Avec Mor**

- `NotificationService` : implémenter l'interface (3 méthodes) et les routes de DS-15. Appeler `resultatPublie(copie)` depuis
  `CorrectionService` quand une copie passe `EVALUE` alors que `resultatsPublies` est déjà vrai.
- Page des notifications et profil : répartition à confirmer.
