# Plateforme d'évaluation EPT — Backend

API REST Spring Boot de la plateforme d'évaluation en ligne (projet transversal DIC1-GIT 2025–2026) :
authentification JWT, passation sécurisée des épreuves (sauvegarde automatique, anti-triche, soumission),
correction automatique des QCM.

- **Stack** : Java 21, Spring Boot 3, Spring Security 6 (JWT), Spring Data JPA / Hibernate, PostgreSQL, Flyway
- **Documentation interactive** : Swagger UI sur `http://localhost:8080/api/swagger-ui/index.html`
- **Collection Postman** : [`docs/Plateforme_Evaluation_Postman_Collection.json`](../docs/Plateforme_Evaluation_Postman_Collection.json)

## Démarrage rapide

Prérequis : JDK 21 et Maven 3.9+.

### Sans installation de base (H2 en mémoire)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2-dev
```

La base est recréée à chaque démarrage. Console H2 : `http://localhost:8080/api/h2-console`
(JDBC URL `jdbc:h2:mem:evaldb`, utilisateur `sa`, mot de passe vide).

### Avec PostgreSQL local (profil `dev`, par défaut)

1. Créer une base vide : `CREATE DATABASE eval_platform_db;`
2. Lancer :

```bash
mvn spring-boot:run
```

Le profil `dev` suppose l'utilisateur `postgres` / `postgrespassword` sur `localhost:5432`
(surchargeable par `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). Flyway crée le schéma au premier démarrage.

### Comptes de démonstration

Créés automatiquement en `dev`, `h2-dev` et en test uniquement (jamais en production) :

| Rôle | Email | Mot de passe |
|---|---|---|
| Administrateur | `admin@ept.sn` | `admin123` |
| Enseignant | `prof@ept.sn` | `prof123` |
| Étudiant (DIC2) | `etudiant@ept.sn` | `etudiant123` |

Deux évaluations ouvertes sont également créées : un QCM « Structures de données » et un exercice de code.

## Configuration

### Profils

| Profil | Base | Schéma | Données de démo | Usage |
|---|---|---|---|---|
| `dev` (défaut) | PostgreSQL local | Flyway | oui | développement |
| `h2-dev` | H2 en mémoire | recréé par Hibernate | oui | démarrage sans installation |
| `test` | H2 en mémoire | recréé par Hibernate | oui | tests automatisés |
| tout autre (ex. `prod`) | PostgreSQL | Flyway | non | déploiement |

### Variables d'environnement

| Variable | Obligatoire hors `dev` | Défaut | Rôle |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | oui | `dev` | profil actif (ex. `prod`) |
| `JWT_SECRET` | **oui** | secret de dev | clé de signature des jetons, en Base64, 256 bits minimum (`openssl rand -base64 64`) |
| `DB_PASSWORD` | **oui** | `postgrespassword` en `dev` | mot de passe PostgreSQL |
| `DB_URL` | non | `jdbc:postgresql://localhost:5432/eval_platform_db` | URL JDBC |
| `DB_USERNAME` | non | `postgres` | utilisateur PostgreSQL |
| `JWT_EXPIRATION_MS` | non | `86400000` (24 h) | durée de validité des jetons |

Sans `JWT_SECRET` ni `DB_PASSWORD`, l'application refuse de démarrer hors profil `dev` : c'est voulu.

### Paramètres métier

| Propriété | Défaut | Rôle |
|---|---|---|
| `passation.taches-planifiees.actif` | `true` | clôture automatique des copies et relance des corrections |
| `passation.taches-planifiees.intervalle-ms` | `60000` | fréquence de ces tâches |
| `securite.login.max-echecs` | `5` | échecs de connexion avant blocage |
| `securite.login.fenetre-minutes` | `15` | fenêtre de comptage des échecs |
| `securite.login.duree-blocage-minutes` | `15` | durée du blocage |

## Base de données et migrations

Le schéma est **versionné par Flyway** (`src/main/resources/db/migration`). Hibernate est en
`ddl-auto=validate` : il vérifie que le schéma correspond aux entités mais ne le modifie jamais.

Pour faire évoluer le modèle :

1. modifier l'entité JPA ;
2. ajouter une migration `V2__description.sql` (puis `V3__…`) avec le SQL PostgreSQL correspondant ;
3. ne **jamais** modifier une migration déjà appliquée (Flyway refuserait de démarrer).

Si le schéma et les entités divergent, l'application ne démarre pas et Hibernate indique la table ou la colonne en cause.

| Migration | Contenu |
|---|---|
| `V1__schema_initial.sql` | schéma complet issu du diagramme de classes, index sur les clés étrangères |
| `V2__version_reponses.sql` | `soumissions.version_reponses` : version des réponses, incrémentée à chaque sauvegarde réussie |
| `V3__evaluation_version.sql` | `evaluations.version` : verrou optimiste (`@Version`), pour qu'un « modifier » et un « publier » simultanés ne s'écrasent pas |

## API

Toutes les routes sont préfixées par `/api`. Les routes autres que le login exigent l'en-tête
`Authorization: Bearer <jeton>` obtenu via `POST /api/auth/login`.

| Méthode | Route | Rôle | Description |
|---|---|---|---|
| POST | `/auth/login` | public | authentification, renvoie le jeton JWT |
| GET | `/auth/me` | connecté | profil de l'utilisateur connecté |
| GET | `/etudiant/evaluations` | étudiant | évaluations de sa promotion et état de sa copie |
| GET | `/etudiant/evaluations/{id}` | étudiant | détail d'une évaluation (durée effective, durée par question du quiz, dates, état de la copie) ; 404 pour un brouillon, 403 hors de sa promotion |
| POST | `/enseignant/evaluations` | enseignant | crée un brouillon avec ses questions (DS-03) |
| GET | `/enseignant/evaluations` | enseignant | ses évaluations, sans les questions |
| GET | `/enseignant/evaluations/{id}` | enseignant (propriétaire) | détail avec questions et bonnes réponses |
| PUT | `/enseignant/evaluations/{id}` | enseignant (propriétaire) | modifie un brouillon (questions remplacées en bloc) |
| DELETE | `/enseignant/evaluations/{id}` | enseignant (propriétaire) | supprime un brouillon |
| POST | `/enseignant/evaluations/{id}/publier` | enseignant (propriétaire) | BROUILLON → PUBLIEE (DS-04) |
| POST | `/enseignant/evaluations/{id}/depublier` | enseignant (propriétaire) | PUBLIEE → BROUILLON tant qu'aucune copie n'existe (DS-04) |
| PUT | `/enseignant/evaluations/{id}/amenagements/{etudiantId}` | enseignant (propriétaire) | tiers-temps ; corps `{ dureeMinutes }` (DS-06) |
| DELETE | `/enseignant/evaluations/{id}/amenagements/{etudiantId}` | enseignant (propriétaire) | supprime un tiers-temps |
| POST | `/soumissions/evaluations/{id}/demarrer` | étudiant | démarre ou reprend une épreuve (copie expirée clôturée, copie remise sans questions) |
| PUT | `/soumissions/{id}/sauvegarder` | étudiant | sauvegarde automatique des réponses (toutes les 30 s) ; corps `{ reponses, versionAttendue }` |
| POST | `/soumissions/{id}/soumettre` | étudiant | soumission finale et correction asynchrone |
| POST | `/soumissions/{id}/changement-onglet` | étudiant | signalement anti-triche |
| GET | `/soumissions/{id}` | étudiant (sa copie), enseignant (ses évaluations), admin | détail d'une copie |

### Erreurs

Toutes les erreurs suivent la norme **RFC 7807** (`application/problem+json`) avec un champ `code` :

| Statut | Cas |
|---|---|
| 400 | validation des champs, requête incorrecte, évaluation non ouverte |
| 401 | jeton absent, invalide ou expiré ; identifiants incorrects |
| 403 | accès refusé : rôle, promotion, copie d'un autre étudiant |
| 404 | ressource introuvable |
| 409 | temps d'épreuve écoulé (`TEMPS_EXPIRE`), copie verrouillée (`SOUMISSION_VERROUILLEE`), réponses enregistrées entre-temps depuis un autre onglet ou appareil (`VERSION_OBSOLETE`, avec `versionActuelle`), évaluation qui n'est plus modifiable (`EVALUATION_NON_MODIFIABLE`), modification simultanée (`CONFLIT_CONCURRENT`) |
| 429 | trop de tentatives de connexion (`TROP_DE_TENTATIVES`, en-tête `Retry-After`) |
| 500 | erreur interne, sans détail technique exposé |

## Règles de passation

Les diagrammes de séquence correspondants sont dans [`docs/diagrammes-sequence`](../docs/diagrammes-sequence).

- **Fin de l'épreuve** : `min(dateDebut + durée effective, dateFermeture)`, la durée effective étant le tiers-temps
  de l'étudiant s'il existe, sinon la durée standard. Une **marge de 2 minutes** s'applique ensuite. Le même calcul
  (`TempsEpreuveService`) sert à la sauvegarde, à la soumission, à la reprise et à la clôture automatique.
- **Démarrage (DS-10)** : les conditions d'ouverture (dates, statut `PUBLIEE` ou `EN_COURS`) ne sont vérifiées qu'à la
  création de la copie. Une copie existante est toujours renvoyée selon son état : en cours (reprise), expirée
  (clôturée à sa fin théorique, sans alerte anti-triche) ou remise (statut seul, sans questions). Une seule copie par
  étudiant et par évaluation (contrainte unique).
- **Sauvegarde (DS-02)** : chaque sauvegarde réussie incrémente `versionReponses`. Si le client envoie une
  `versionAttendue` différente de la version du serveur, la sauvegarde est refusée (409 `VERSION_OBSOLETE`) au lieu
  d'écraser les réponses enregistrées depuis un autre onglet. `versionAttendue` est facultative : sans elle, aucun
  contrôle n'est fait.
- **Soumission (DS-01)** : dans les temps (marge comprise), la copie passe à `SOUMIS` et la correction est lancée
  après le commit. Au-delà, la copie est figée à sa dernière sauvegarde, une alerte anti-triche `ELEVE` est enregistrée
  et le serveur répond 409 `TEMPS_EXPIRE` sans annuler ces écritures (`noRollbackFor`). Soumettre une copie déjà
  remise renvoie 200 sans effet.
- **Clôture automatique (DS-11)** : toutes les 60 s, les copies `EN_COURS` expirées sont clôturées comme ci-dessus
  (sans alerte), et les copies restées `SOUMIS` plus de 5 minutes voient leur correction relancée.
- **Correction** : sans effet si la copie n'est plus `SOUMIS` ; une note posée par l'enseignant n'est jamais écrasée.

## Règles de création d'évaluation

Diagrammes : DS-03 (création et modification), DS-04 (publication), DS-05 (statuts automatiques), DS-06 (tiers-temps).

- **Brouillon d'abord** : toute évaluation créée est en `BROUILLON`. L'enseignant vient du jeton, jamais du corps de la
  requête, et ne voit que ses évaluations (403 sur celle d'un autre).
- **Modification et suppression** : uniquement en `BROUILLON` (sinon 409 `EVALUATION_NON_MODIFIABLE`). Pour corriger une
  évaluation publiée, il faut d'abord la dépublier, ce qui n'est possible que tant qu'aucune copie n'existe.
- **Règles des questions** : QCM = au moins 2 choix et au moins 1 correct (un seul si `estChoixMultiple = false`) ;
  quiz = règles du QCM + `dureeQuestion` > 0 ; code = au moins 1 cas de test ; points > 0. L'ordre est la position
  dans la liste. Une règle violée renvoie 400 (« Question 3 : … ») et rien n'est enregistré.
- **Publication** : exige au moins une question et une `dateFermeture` future. Le passage à `EN_COURS` puis `TERMINEE`
  est fait par `EvaluationStatutService` toutes les 60 s (une évaluation publiée déjà fermée va directement à
  `TERMINEE`). Cette tâche ne soumet aucune copie : voir DS-01 et la clôture automatique.
- **Tiers-temps** : la durée doit être au moins égale à la durée standard, l'étudiant doit être dans une promotion de
  l'évaluation, et l'aménagement est **verrouillé dès que l'étudiant a commencé sa copie** (409), car
  `TempsEpreuveService` le relit à chaque calcul.
- **Concurrence** : `Evaluation` porte un `@Version`. Deux écritures simultanées (modifier / publier) : la seconde reçoit
  409 `CONFLIT_CONCURRENT`.
- **Réponses de l'enseignant** : `EvaluationResponse` contient les bonnes réponses ; elle ne doit jamais être servie à un
  étudiant (l'étudiant reçoit `EvaluationEtudiantResponse`, sans question).

## Tests

`mvn test` lance les tests unitaires (Mockito, horloge figée) et les tests d'intégration HTTP sur H2.
`MigrationPostgresIntegrationTest` exige Docker (Testcontainers).

Si Mockito échoue avec « Could not self-attach to current VM using external process » (observé sous Windows),
lancer avec `JAVA_TOOL_OPTIONS=-Djdk.attach.allowAttachSelf=true`.

## Architecture

```
sn.ept.eval
├── controller/   Endpoints REST
├── service/      Logique métier
├── repository/   Accès aux données (Spring Data JPA)
├── model/        Entités JPA alignées sur le diagramme de classes UML
├── dto/          Objets échangés avec le frontend
├── security/     JWT, Spring Security, limitation des tentatives de connexion
├── exception/    Exceptions métier et gestionnaire RFC 7807
└── config/       Configuration (async, horloge, audit JPA, OpenAPI, données de démo)
```

La passation est répartie en services à responsabilité unique :

| Service | Responsabilité |
|---|---|
| `SoumissionService` | cycle de vie d'une copie : démarrer, sauvegarder, soumettre, consulter |
| `EvaluationEtudiantService` | liste des évaluations de l'étudiant, durée effective et état de sa copie |
| `TempsEpreuveService` | durée effective (aménagements), fin d'épreuve, marge de 2 min, temps restant |
| `EnregistrementReponsesService` | validation et enregistrement des réponses selon le type de question |
| `AntiTricheService` | changements d'onglet (alertes au 3e et au 5e) et soumissions tardives |
| `SoumissionAccesService` | étudiant connecté, propriété des copies, consultation selon le rôle |
| `SoumissionMapper` | conversion en DTO, sans jamais exposer les bonnes réponses |
| `CorrectionService` | correction automatique asynchrone (QCM et quiz chronométrés), sans effet si la copie n'est plus SOUMIS |
| `CorrectionDeclencheur` | lancement de la correction après validation de la transaction |
| `ClotureCopiesService` + `PassationScheduler` | clôture des copies abandonnées, relance des corrections bloquées |

Concurrence : sauvegarde, soumission, clôture automatique, reprise et correction verrouillent la copie
(`SELECT ... FOR UPDATE`) ; une réponse ne peut pas être écrite après le passage à `SOUMIS` et une copie
n'est corrigée qu'une fois.

## Tests

```bash
mvn test
```

- **Tests unitaires** (Mockito, horloge figée) : règles de passation (temps, reprise, version des réponses), clôture
  automatique, liste des évaluations, anti-triche, limitation des connexions, validation des DTO, format des erreurs.
- **Tests d'intégration** (H2) : parcours complets de passation et de correction, sécurité HTTP des endpoints, et
  scénarios concurrents (démarrages simultanés, soumissions simultanées, correction relancée, sauvegarde depuis un
  onglet resté sur une ancienne version).
- **Test PostgreSQL** (`MigrationPostgresIntegrationTest`, Testcontainers) : applique la migration Flyway sur un vrai
  PostgreSQL et rejoue une passation. Il nécessite Docker ; sans Docker, il est ignoré localement mais s'exécute en CI.

La CI GitHub Actions (`.github/workflows/ci.yml`) compile et lance les tests du backend et du frontend à chaque push
sur `main`, `feature/**`, `feat/**`, `fix/**`, `refactor/**` et `docs/**`, ainsi qu'à chaque pull request vers `main`.
