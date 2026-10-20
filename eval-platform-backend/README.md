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

## API

Toutes les routes sont préfixées par `/api`. Les routes autres que le login exigent l'en-tête
`Authorization: Bearer <jeton>` obtenu via `POST /api/auth/login`.

| Méthode | Route | Rôle | Description |
|---|---|---|---|
| POST | `/auth/login` | public | authentification, renvoie le jeton JWT |
| GET | `/auth/me` | connecté | profil de l'utilisateur connecté |
| POST | `/soumissions/evaluations/{id}/demarrer` | étudiant | démarre ou reprend une épreuve |
| PUT | `/soumissions/{id}/sauvegarder` | étudiant | sauvegarde automatique des réponses (toutes les 30 s) |
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
| 409 | temps d'épreuve écoulé (`TEMPS_EXPIRE`), copie verrouillée (`SOUMISSION_VERROUILLEE`) |
| 429 | trop de tentatives de connexion (`TROP_DE_TENTATIVES`, en-tête `Retry-After`) |
| 500 | erreur interne, sans détail technique exposé |

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
| `TempsEpreuveService` | durée effective (aménagements), fin d'épreuve, marge de 2 min, temps restant |
| `EnregistrementReponsesService` | validation et enregistrement des réponses selon le type de question |
| `AntiTricheService` | changements d'onglet (alertes au 3e et au 5e) et soumissions tardives |
| `SoumissionAccesService` | étudiant connecté, propriété des copies, consultation selon le rôle |
| `SoumissionMapper` | conversion en DTO, sans jamais exposer les bonnes réponses |
| `CorrectionService` | correction automatique asynchrone (QCM et quiz chronométrés) |
| `CorrectionDeclencheur` | lancement de la correction après validation de la transaction |
| `ClotureCopiesService` + `PassationScheduler` | clôture des copies abandonnées, relance des corrections bloquées |

## Tests

```bash
mvn test
```

- **Tests unitaires** (Mockito, horloge figée) : règles de passation, anti-triche, limitation des connexions, validation des DTO.
- **Tests d'intégration** (H2) : parcours complets de passation, correction, concurrence, sécurité HTTP.
- **Test PostgreSQL** (`MigrationPostgresIntegrationTest`, Testcontainers) : applique la migration Flyway sur un vrai
  PostgreSQL et rejoue une passation. Il nécessite Docker ; sans Docker, il est ignoré localement mais s'exécute en CI.

La CI GitHub Actions (`.github/workflows/ci.yml`) compile et lance tous les tests à chaque push sur `main`,
`feat/**` et `refactor/**`, ainsi qu'à chaque pull request vers `main`.
