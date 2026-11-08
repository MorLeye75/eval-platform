# Passation, correction et résultats — travaux des 7 et 8 novembre 2026

Branche `feature/passation-ds01-ds02`, poussée sur `origin`. Périmètre : Mor (interface étudiant, passation,
correction automatique, résultats). Ce document résume ce qui a changé, comment le faire tourner et ce qui
reste à faire, notamment ce qui concerne le module d'Aminou.

## En bref

- Une revue de code complète a été traitée : sécurité, intégrité des copies, performances, accessibilité.
- Les écrans étudiants n'utilisent plus de données simulées pour la passation et les résultats. Le fichier
  `mocks/evaluations.ts`, qui exposait les bonnes réponses dans le navigateur, a été supprimé.
- Le code des étudiants est corrigé automatiquement par **Judge0**, au prorata des cas de test réussis (DS-12).
- L'étudiant consulte ses résultats et son tableau de bord sur l'API (DS-13).
- Quatre diagrammes de séquence ont été ajoutés ou alignés sur le code : DS-11, DS-12, DS-13 et DS-14.
- Tests : **172 côté backend** (dont PostgreSQL réel via Docker) et **132 côté frontend**, tous verts.

## 1. Revue de code (7 novembre)

Toutes les corrections sont couvertes par un test écrit pour reproduire le défaut avant de le corriger.

| Domaine | Correction |
|---|---|
| Passation | Plus de copie vide soumise à l'ouverture (chrono à 0 au premier rendu). Une seule réponse par question et par copie (V3). Plus d'énoncés renvoyés après la remise. Réponses gardées localement à chaque frappe, délai maximal de 15 s par requête. Fenêtre de remise verrouillée pendant l'envoi, heure de remise fixée par le serveur. Une modification annulée n'est plus restaurée au rechargement. |
| Correction | Plus de notes partielles en cas d'erreur (transaction annulée). Une file de correction pleine ne provoque plus d'erreur 500. |
| Sécurité et configuration | Plus de profil `dev` par défaut. Démarrage refusé sans `DB_PASSWORD` ni `JWT_SECRET`. Origines CORS configurables, Swagger désactivé hors développement. |
| Authentification | Session vidée à la déconnexion, rôle revalidé par le serveur (`/auth/me`). Limite de tentatives par IP en plus du couple email + IP, avec 429 affiché. Session d'un autre compte effacée sur un poste partagé. Connexion sans modifier l'audit du compte. |
| Erreurs | Champ `code` sur toutes les erreurs ; 409 `CONFLIT_DONNEES` / `VERROU_INDISPONIBLE`. |
| Performances | Clôture des copies en lot, une transaction courte par copie. Tables de liaison avec clé primaire (V4), proposition IA chargée à la demande. |
| Accessibilité | Vrais liens et boutons nommés, accordéons accessibles. |

Écarté sur décision de Mor : conversion des DTO existants en `record` (les nouveaux DTO simples peuvent l'être).

## 2. Passation sans données simulées (7 et 8 novembre)

- **Anti-triche** : chaque sortie de page est signalée au serveur, et le compteur survit au rechargement.
- **Quiz chronométré** : l'ordre et le temps de chaque question sont imposés par le serveur. Toute réponse
  hors de la question en cours ou hors délai est refusée (V5, `POST /soumissions/{id}/quiz/suivante`).
- **Écran unique** `/examen/:id/epreuve` pour toute épreuve hors quiz, épreuves mixtes comprises. Les anciennes
  routes `/qcm`, `/ouverte` et `/code` redirigent vers lui.
- **Code** : le langage choisi est enregistré avec la réponse (V6). L'exécution simulée est supprimée.
- **Création (module d'Aminou)** : le bouton « Publier » est désactivé tant que l'API de création n'existe pas.
  La constante `ENREGISTREMENT_DISPONIBLE` dans `Previsualisation` est à passer à `true` à ce moment-là.

## 3. Correction automatique du code via Judge0 — DS-12

Diagramme : [`DS-12-correction-code-judge0.puml`](diagrammes-sequence/DS-12-correction-code-judge0.puml).

**Principe.** Chaque cas de test est exécuté par Judge0 auto-hébergé. La note d'une question de code vaut
`points × cas réussis / nombre de cas`, arrondie à 2 décimales ; les cas masqués comptent. Un code vide vaut 0.
Une question sans cas de test est laissée à l'enseignant.

**Déroulement en trois phases**, pour ne garder ni verrou ni transaction pendant les appels à Judge0 :

1. **Prise en charge sous verrou.** La copie est marquée `correction_demarree_le`, qui sert de jeton, et les
   données à exécuter sont copiées.
2. **Exécutions hors transaction, 10 min au plus.** Le premier cas part seul : une erreur de compilation fait
   échouer tous les cas sans autre exécution. Les cas suivants partent par lots de 20.
3. **Écriture sous verrou**, seulement si la copie est toujours `SOUMIS` et que le jeton n'a pas changé.

**Pannes.**

- *Judge0 indisponible* (réseau, erreur 5xx, statuts 13 et 14, délai dépassé) : nouvel essai 1, 2, 4 puis 8 min
  plus tard. À la 5e tentative, la copie passe en correction manuelle avec ses QCM notés.
- *Erreur imprévue* : elle compte de la même façon.
- *Relance planifiée* : elle reprend les copies dont `prochaine_tentative_le` est passée, 50 par minute au plus.

**Files de correction.** Les évaluations avec du code passent par un exécuteur limité au nombre de workers
Judge0. Les QCM et quiz passent par un exécuteur rapide et n'attendent jamais derrière Judge0.

**Configuration** (voir le README du backend) :

| Variable | Défaut | Rôle |
|---|---|---|
| `JUDGE0_URL` | `http://localhost:2358` | Judge0 sur le réseau interne, jamais exposé publiquement |
| `JUDGE0_TOKEN` | vide | en-tête `X-Auth-Token`, si Judge0 en exige un |
| `JUDGE0_CONCURRENCE` | `2` | corrections avec du code simultanées, égal au nombre de workers Judge0 |

Langages pris en charge (identifiants Judge0 CE 1.13) : C 50, C++ 54, Java 62, JavaScript 63, Python 71.

**Pour les enseignants.** Un exercice de code est un **programme complet** : l'entrée du cas de test arrive sur
l'entrée standard, et la sortie standard est comparée à la sortie attendue. Les espaces en fin de ligne ne
comptent pas.

**Vérifié.**

- Format de l'API Judge0 contrôlé dans son code source (v1.13.1) : `message` en base64, liste de jetons d'un
  lot, identifiants des langages, limites maximales acceptées.
- Parcours réel avec un Judge0 injoignable : tentatives comptées et relancées comme prévu.

**Pas encore vérifié.** L'exécution sur un vrai Judge0. Judge0 1.13 exige les cgroups v1, alors que Docker
Desktop sous WSL2 est en v2. Il faut soit modifier la configuration WSL, soit utiliser une machine Linux.

## 4. Clôture automatique des copies — DS-11

Diagramme : [`DS-11-cloture-automatique.puml`](diagrammes-sequence/DS-11-cloture-automatique.puml).

Les copies `EN_COURS` dont le temps est écoulé, marge de 2 min comprise, sont figées à leur dernière
sauvegarde puis corrigées.

**Défaut trouvé et corrigé.** Sur PostgreSQL, la clôture attendait indéfiniment une copie verrouillée
ailleurs, ce qui bloquait toute la tâche planifiée : plus de clôtures, plus de relances de correction. Les
tests sur H2 ne le montraient pas, car H2 abandonne un verrou au bout d'1 s. La clôture verrouille désormais
en `NOWAIT` : une copie occupée est reprise au passage suivant. Un test PostgreSQL (Testcontainers)
reproduit le cas.

## 5. Consultation des résultats par l'étudiant — DS-13

Diagramme : [`DS-13-consultation-resultats.puml`](diagrammes-sequence/DS-13-consultation-resultats.puml).

| Route | Réponse |
|---|---|
| `GET /api/soumissions/{id}/resultats` | `NON_REMISE` (renvoi vers l'épreuve), `EN_ATTENTE` (aucune note, même automatique), ou `PUBLIES` avec le détail par question. 403 pour la copie d'un autre étudiant. |
| `GET /api/etudiant/performances` | Notes sur 20, moyenne de la promotion et écart à cette moyenne, calculés sur les mêmes évaluations. |

**Règles de confidentialité**, validées par Mor :

- Les bonnes réponses des QCM ne sont visibles qu'**après la fermeture + 2 min**. Plus personne ne compose
  alors, puisque chaque épreuve est plafonnée à la fermeture, tiers-temps compris.
- Les **cas de test masqués** sont réduits à leur verdict (réussi, mauvaise réponse, temps dépassé, erreur de
  compilation ou d'exécution) et à leur temps. Le verdict est enregistré par la correction (V8). Les cas
  visibles sont affichés en entier : l'étudiant les voyait déjà pendant l'épreuve.
- La **moyenne de la promotion** n'est communiquée qu'à partir de 5 copies notées.
- **Pas de rang** dans la promotion, seulement l'écart à la moyenne, comme le demande le cahier des charges.
- La **proposition de l'IA** n'est jamais renvoyée : l'étudiant ne voit que la note et le commentaire validés
  par l'enseignant.

Les écrans `ResultatsDetail` et `TableauBordPerformance` sont branchés sur ces routes. Les liens utilisent
l'identifiant de la **copie**.

## 6. Connexion — DS-14

Diagramme : [`DS-14-connexion.puml`](diagrammes-sequence/DS-14-connexion.puml). Il documente le code existant :

- limite des tentatives (5 échecs par couple email + IP, 100 par IP, sur 15 min ; réponse 429) ;
- même message 401 que le compte existe ou non ;
- jeton de 24 h, avec rôle et état du compte relus en base à chaque requête ;
- revalidation au démarrage par `/auth/me`, et effacement de la session à la déconnexion.

**Limite connue.** La déconnexion n'appelle pas le serveur : un jeton copié reste valable jusqu'à son
expiration, 24 h au plus.

## 7. Migrations Flyway ajoutées

| Version | Contenu |
|---|---|
| V3 | Une seule réponse par question et par copie (doublons supprimés, contrainte unique) |
| V4 | Clés primaires et index des tables de liaison |
| V5 | Quiz : question en cours et heure de début |
| V6 | Langage de la réponse de code |
| V7 | Suivi de la correction : `correction_demarree_le`, `tentatives_correction`, `prochaine_tentative_le`, index partiel de relance |
| V8 | Verdict des cas de test |

Toutes sont vérifiées sur un vrai PostgreSQL par `MigrationPostgresIntegrationTest` : le schéma est validé contre
les entités, avec un parcours de passation, la relance des corrections et les statistiques de résultats.

## 8. Lancer les tests

```bash
# Backend : H2, plus PostgreSQL via Testcontainers si Docker est lancé (ignoré sinon)
cd eval-platform-backend && mvn clean test

# Frontend
cd eval-platform-frontend && npm test && npx tsc -b --noEmit && npm run lint
```

`mvn clean test` plutôt qu'une compilation incrémentale : celle-ci a déjà produit des `NoClassDefFoundError`.

**Collection Postman** (`docs/Plateforme_Evaluation_Postman_Collection.json`). Elle couvre la connexion et
`/auth/me`, la passation QCM, le quiz, l'épreuve de code et les résultats. Parcours étudiant, à lancer sur une
base neuve (profil `h2-dev`) :

```bash
postman collection run docs/Plateforme_Evaluation_Postman_Collection.json \
  --env-var "baseUrl=http://localhost:8080/api" \
  -i "1.1 Login Étudiant (Aminou Niang)" -i "1.4 Profil de l'utilisateur connecté (/auth/me)" \
  -i "02 - Passation & Soumission (DS-01, DS-02, DS-10)" -i "03 - Épreuve de code (DS-12)" \
  -i "04 - Résultats de l'étudiant (DS-13)"
```

## 9. Reste à faire

**Pour Mor (fin de projet)**

- Faire tourner un **vrai Judge0** : Docker Compose à écrire, puis machine Linux ou WSL passé en cgroups v1.
- Brancher l'**accueil étudiant** (`DashboardEtudiant`), encore simulé : son lien vers une copie corrigée ne mène
  plus à rien. Il peut réutiliser `/etudiant/evaluations` et `/etudiant/performances`. La page des notifications
  et le profil sont aussi simulés.
- Révocation des jetons à la déconnexion (liste de révocation ou jetons plus courts), si on la juge utile.

**À voir avec Aminou**

- **Publication** : la notification « Résultats publiés » doit porter l'identifiant de la copie de chaque
  étudiant (une notification par étudiant), car la page de résultats est adressée par copie.
- **Création d'évaluation** : passer `ENREGISTREMENT_DISPONIBLE` à `true` avec l'API. Rédiger les exercices de
  code comme des programmes complets.
- **Option future** à la publication : « corrigé jamais visible », pour réutiliser un sujet.
- Bouton enseignant « Relancer la correction automatique » d'une copie passée en correction manuelle
  (remise à zéro des tentatives, retour à `SOUMIS`).
- **Attribution de DS-15**, correction assistée par IA. Le cahier des charges met l'intégration OpenAI dans le
  même rôle que Judge0, mais l'écran de validation est côté enseignant.
- **Épreuves mixtes** : leur besoin n'est pas confirmé par le cahier des charges. Elles sont gérées côté
  passation ; l'autre choix était de les interdire dans le Builder.
