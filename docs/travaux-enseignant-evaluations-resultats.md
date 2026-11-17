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
| `DS-06-amenagement-temps.puml` | tiers-temps : plage, verrou de l'évaluation, copie déjà commencée, liste des étudiants de l'écran |
| `DS-07-publication-resultats.puml` | publication des résultats, copies pas encore notées |
| `DS-08-correction-ia-validation.puml` | propositions IA (asynchrones), validation, rejet, note manuelle, note finale de la copie |
| `DS-18-vue-ensemble-admin.puml` | tableau de bord, supervision globale et statistiques globales de l'administrateur |
| `DS-17-supervision-temps-reel.puml` | supervision en lecture seule, sondage, réponses réellement données, alertes |
| `DS-09-statistiques-export.puml` | statistiques sur 20 d'une évaluation, export CSV et protection du fichier |
| `DS-16-administration.puml` | comptes, désactivation et garde-fous, mots de passe provisoires, promotions, import CSV tout ou rien |

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
| Correction IA (DS-08) | l'IA propose, l'enseignant valide ou rejette ; la dernière note passe la copie EVALUE et notifie l'étudiant si les résultats sont publiés ; note manuelle en repli |
| Duplication (DS-03) | sans nouvelle route : le détail est relu puis recréé en brouillon « Copie de … » (questions, dates, promotions) ; ni tiers-temps, ni copies, ni notes ; les dates passées sont à corriger avant publication |
| Tiers-temps (DS-06) | écran par évaluation : liste des étudiants attendus avec leur durée ; champ de saisie seulement si le serveur acceptera la modification (`modifiable`), durée contrôlée avant l'envoi |
| Vue d'ensemble admin (DS-18) | trois lectures seules calculées à la demande ; services affichés seulement s'ils sont réellement connus ; moyennes sur 20 par promotion, « — » sans copie notée |
| Supervision (DS-17) | avancement d'une évaluation ouverte en lecture seule, relu toutes les 15 s ; réponses réellement données comptées (les réponses vides de la sauvegarde automatique ne comptent pas) |
| Statistiques et export (DS-09) | statistiques sur 20 calculées à la demande sur les copies notées ; export CSV d'une ligne par étudiant attendu, absents compris |
| Administration (DS-16) | comptes (création, modification, désactivation, mot de passe provisoire), promotions, import CSV d'étudiants tout ou rien ; un compte n'est jamais supprimé |
| Notifications | interface `NotificationService` (3 événements) + implémentation par défaut sans effet (`@Bean @Fallback`), jamais préférée à celle du module de Mor |

### Frontend

| Écran | Avant | Maintenant |
|---|---|---|
| Assistant de création / modification | mocks, bouton « Publier » désactivé | API ; choix des promotions ; erreurs du serveur affichées ; brouillon local effacé seulement après confirmation |
| Modification d'une évaluation | questions perdues (mock) | détail chargé depuis l'API ; message si l'évaluation n'est plus un brouillon |
| Mes évaluations | mocks, boutons sans effet | API ; publier, repasser en brouillon, supprimer, publier les résultats ; actions selon l'état |
| Publication des résultats | notes d'étudiants inventées, « publication » simulée | évaluations terminées de l'API, vraie route, bilan des copies notifiées |
| Validation des corrections IA | mocks | API : génération pour une évaluation terminée, relecture automatique tant que des propositions se génèrent, valider / rejeter avec note, erreurs du serveur affichées |
| Supervision | mocks (étudiants et alertes inventés) | API : états, progression, alertes triées, sondage de 15 s, dernier état gardé si une lecture échoue |
| Tableau de bord | chiffres fixes | synthèse des évaluations et corrections IA de l'API, « — » pendant le chargement |
| Statistiques et export | mocks | API : indicateurs, distribution, taux par question, questions difficiles ; CSV téléchargé depuis l'API (le PDF et l'Excel natif sont retirés) |
| Gestion des comptes (administrateur) | mocks, boutons désactivés | API : liste filtrée, création, modification, désactivation, mot de passe provisoire (affiché une fois), promotions, import CSV avec lignes en erreur |
| Formulaire des cas de test | — | rappel « programme complet : stdin → stdout », entrée facultative |

## Décisions prises

| Décision | Raison |
|---|---|
| Un brouillon n'exige pas « ouverture + durée ≤ fermeture » | les dates se règlent souvent en dernier ; la règle est vérifiée à la publication et au tiers-temps, et signalée dès l'étape 1 quand on publie immédiatement |
| Tiers-temps refusé (400) plutôt que simple avertissement | la fin d'une épreuve est plafonnée à la fermeture : un tiers-temps plus long serait sans effet, donc trompeur |
| Résultats publiables seulement après fermeture + 2 min | même règle que DS-13 pour dévoiler les corrigés : plus personne ne compose |
| Copies pas encore notées : publication autorisée | elles restent « en attente » ; leur étudiant est notifié à la notation (`resultatPublie`) |
| Notifications créées dans la transaction de l'événement | jamais « publié sans notification », jamais de notification pour un événement annulé |
| `NotificationService` en interface + `@Bean @Fallback` | pas de conflit avec le module de Mor : son bean est injecté à la place du mien, dans n'importe quel ordre d'enregistrement ; `@ConditionalOnMissingBean` n'est fiable que dans une auto-configuration |
| Supervision par sondage (15 s), pas de WebSocket | un sondage de lecture ne verrouille rien et se déploie sans infrastructure ; un temps réel poussé n'apporterait rien pour une relecture toutes les quinze secondes |
| Statistiques sur 20, calculées à la demande | des barèmes différents restent comparables ; aucune table à tenir à jour, donc toujours exactes après une note validée (DS-08) |
| Export CSV seulement (pas de PDF ni d'Excel natif) | le CSV s'ouvre dans Excel ; un PDF ou un .xlsx exigerait une bibliothèque lourde pour un gain faible |
| Désactiver plutôt que supprimer un compte | les copies, évaluations, notes et notifications y sont rattachées ; la désactivation est immédiate (jetons refusés à la requête suivante) |
| Import CSV tout ou rien, 500 lignes | une erreur de saisie ne doit pas laisser un import à moitié fait ; BCrypt est lent (hachage en parallèle) |
| Connecteur IA simulé par défaut | pas de clé ni de fournisseur imposé ; la note de l'IA est une proposition, jamais écrite sans validation ; le vrai modèle se branche derrière `CorrecteurIA` (aucune identité d'étudiant envoyée) |
| Génération déclenchée par l'enseignant, évaluation terminée | pas de coût d'appels imprévu, relançable sans doublon |
| Copie `EVALUE` : plus de modification de note | hors périmètre ; à reprendre si l'enseignant doit corriger avant publication |
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

- **DS-08, suite** : connecteur vers un vrai modèle de langage (fournisseur, clé `LLM_API_KEY` et coût à décider avec
  l'encadrant), modification d'une note après coup. Les notes manuelles ont leur écran (section « À noter à la main » de la validation IA).
- **DS-09, suite** : comparaison avec les sessions précédentes (aucune donnée de référence), export PDF / Excel natif si l'encadrant l'exige.
- **DS-16, suite** : gestion des promotions (modifier, supprimer), révocation des sessions d'un compte dont on réinitialise le mot de passe, changement de mot de passe obligatoire à la première connexion, paramètres système de l'espace administrateur (aperçu non branché : aucune configuration modifiable à chaud n'existe côté serveur).
- Écran encore simulé : paramètres système de l'administrateur (signalé comme aperçu à l'écran).

**Avec Mor**

- `NotificationService` : implémenter l'interface (3 méthodes) et les routes de DS-15. Appeler `resultatPublie(copie)` depuis
  `CorrectionService` quand une copie passe `EVALUE` alors que `resultatsPublies` est déjà vrai.
- Page des notifications et profil : répartition à confirmer.
