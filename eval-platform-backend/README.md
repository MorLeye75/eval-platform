# Plateforme d'évaluation EPT — Backend

API REST Spring Boot de la plateforme d'évaluation en ligne (projet transversal DIC1-GIT 2025–2026) :
authentification JWT, passation sécurisée des épreuves (sauvegarde automatique, anti-triche, soumission),
correction automatique des QCM.

- **Stack** : Java 21, Spring Boot 3, Spring Security 6 (JWT), Spring Data JPA / Hibernate, PostgreSQL, Flyway
- **Documentation interactive** : Swagger UI sur `http://localhost:8080/api/swagger-ui/index.html` (profils `dev` et `h2-dev`, ou `SWAGGER_ENABLED=true`)
- **Collection Postman** : [`docs/Plateforme_Evaluation_Postman_Collection.json`](../docs/Plateforme_Evaluation_Postman_Collection.json)

## Démarrage rapide

Prérequis : JDK 21 et Maven 3.9+.

### Sans installation de base (H2 en mémoire)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2-dev
```

La base est recréée à chaque démarrage. Console H2 : `http://localhost:8080/api/h2-console`
(JDBC URL `jdbc:h2:mem:evaldb`, utilisateur `sa`, mot de passe vide).

### Avec PostgreSQL local (profil `dev`)

1. Créer une base vide : `CREATE DATABASE eval_platform_db;`
2. Lancer :

```bash
mvn spring-boot:run
```

`mvn spring-boot:run` active le profil `dev` (propriété `spring-boot.run.profiles` du `pom.xml`) ;
un lancement depuis l'IDE ou le jar doit le demander : `SPRING_PROFILES_ACTIVE=dev`.
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
| `dev` (défaut de `mvn spring-boot:run`) | PostgreSQL local | Flyway | oui | développement |
| `h2-dev` | H2 en mémoire | recréé par Hibernate | oui | démarrage sans installation |
| `test` | H2 en mémoire | recréé par Hibernate | oui | tests automatisés |
| tout autre (ex. `prod`) | PostgreSQL | Flyway | non | déploiement |

### Variables d'environnement

| Variable | Obligatoire hors `dev` | Défaut | Rôle |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | oui | aucun (`dev` avec `mvn spring-boot:run`) | profil actif (ex. `prod`) |
| `JWT_SECRET` | **oui** | secret de dev | clé de signature des jetons, en Base64, 256 bits minimum (`openssl rand -base64 64`) |
| `DB_PASSWORD` | **oui** | `postgrespassword` en `dev` | mot de passe PostgreSQL |
| `DB_URL` | non | `jdbc:postgresql://localhost:5432/eval_platform_db` | URL JDBC |
| `DB_USERNAME` | non | `postgres` | utilisateur PostgreSQL |
| `JWT_EXPIRATION_MS` | non | `86400000` (24 h) | durée de validité des jetons |
| `CORS_ALLOWED_ORIGINS` | **oui** | `http://localhost:5173,http://127.0.0.1:5173` | origines du front autorisées, séparées par des virgules (ex. `https://eval.ept.sn`) ; pas de `*` |
| `SWAGGER_ENABLED` | non | `false` (`true` en `dev` et `h2-dev`) | sert la documentation OpenAPI et Swagger UI |
| `JUDGE0_URL` | oui, pour corriger le code | `http://localhost:2358` | Judge0 auto-hébergé, sur le réseau interne uniquement (DS-12) |
| `JUDGE0_TOKEN` | si Judge0 en exige un | vide | jeton envoyé dans l'en-tête `X-Auth-Token` (`AUTHN_TOKEN` de Judge0) |
| `JUDGE0_CONCURRENCE` | non | `2` | corrections avec du code simultanées : le nombre de workers Judge0 |
| `CORRECTION_IA_FOURNISSEUR` | non | `simule` | `simule` = correcteur sans réseau (démonstration, tests) ; `llm` = vrai modèle de langage (DS-08). **Avec `llm`, le texte des réponses des étudiants est envoyé au fournisseur** : accord de l'établissement requis, ou réponses fictives |
| `LLM_API_KEY` | **oui** si `llm` | vide | clé d'API du fournisseur (jamais dans le dépôt : environnement ou `.env`). Sans elle, le démarrage est refusé avec `llm` |
| `LLM_URL` | non | `https://api.groq.com/openai/v1/chat/completions` | point d'accès au format « chat completions » d'OpenAI (Groq, Gemini, OpenRouter…) ; `https` obligatoire hors poste local |
| `LLM_MODEL` | non | `openai/gpt-oss-120b` | nom du modèle chez le fournisseur |
| `LLM_DELAI_SECONDES` | non | `20` | délai maximal d'un appel ; au-delà, la réponse reste à noter à la main |
| `LLM_EFFORT_RAISONNEMENT` | non | `low` | propre aux modèles à raisonnement (gpt-oss) : `low`, `medium`, `high` ; **vide pour les autres modèles** |
| `FRONTEND_URL` | **oui**, pour le mot de passe oublié | `http://localhost:5173` | adresse du front dans le lien envoyé par e-mail (`FRONTEND_URL/reset-password/<jeton>`) |
| `SPRING_MAIL_HOST` | **oui** en production | aucun | serveur SMTP ; avec `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` et `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`. Sans lui, les liens de réinitialisation sont écrits dans les logs (démonstration locale seulement : ils donnent accès au compte) |
| `MAIL_EXPEDITEUR` | non | `no-reply@ept.sn` | adresse d'expédition des e-mails |
| `FORWARD_HEADERS_STRATEGY` | derrière un proxy | `none` | `native` derrière un reverse proxy (nginx, traefik…) pour lire l'IP réelle du client dans `X-Forwarded-For` ; laisser `none` sans proxy (sinon un client pourrait inventer son IP) |

Sans `JWT_SECRET` ni `DB_PASSWORD`, l'application refuse de démarrer hors profil `dev` : c'est voulu.
Stratégie de mise en production (architecture, choix, étapes, sécurité, sauvegardes, risques) :
[`docs/strategie-deploiement.md`](../docs/strategie-deploiement.md).
L'absence de `DB_PASSWORD` est détectée avant toute connexion à la base (`ControleConfigurationObligatoire`) :
sans ce contrôle, Spring enverrait la chaîne `${DB_PASSWORD}` comme mot de passe.
Un `JWT_SECRET` qui n'est pas en Base64 ou fait moins de 256 bits bloque aussi le démarrage.
Oublier `SPRING_PROFILES_ACTIVE` au déploiement ne bascule jamais en `dev` (pas de comptes de démonstration
ni de secret par défaut) : l'application démarre sans profil et exige ces variables.

### Paramètres métier

| Propriété | Défaut | Rôle |
|---|---|---|
| `passation.taches-planifiees.actif` | `true` | clôture automatique des copies et relance des corrections |
| `passation.taches-planifiees.intervalle-ms` | `60000` | fréquence de ces tâches |
| `correction.judge0.cpu-time-limit` / `wall-time-limit` | `2` / `5` s | limites d'exécution d'un cas de test |
| `correction.judge0.memory-limit-ko` | `256000` | mémoire d'un cas de test (Java en a besoin) |
| `correction.judge0.duree-max-execution` | `10m` | durée maximale de toutes les exécutions d'une copie |
| `correction.judge0.langages.*` | c 50, cpp 54, java 62, python 71, javascript 63 | `language_id` de Judge0 CE 1.13 |
| `securite.login.max-echecs` | `5` | échecs de connexion avant blocage d'un couple (email, IP) |
| `securite.login.max-compteurs` | `10000` | couples (email, IP) gardés en mémoire ; au-delà, les plus anciens sont évincés. Pas de blocage par IP seule : derrière le NAT d'un campus, il bloquerait tous les étudiants |
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
| `V3__reponse_unique_par_question.sql` | une seule réponse par question et par copie : suppression des doublons existants, contrainte `uk_reponses_soumission_question` |
| `V4__cles_tables_de_liaison.sql` | clés primaires composites et index inverses sur `evaluation_promotions` et `reponse_qcm_choix` (après dédoublonnage) |
| `V5__quiz_question_en_cours.sql` | quiz chronométré : question en cours et heure de son début |
| `V6__langage_reponse_code.sql` | langage choisi pour une réponse de code |
| `V7__suivi_correction_automatique.sql` | suivi de la correction : jeton, tentatives, prochaine tentative, index partiel de relance |
| `V8__verdict_cas_de_test.sql` | verdict de chaque cas de test (réussi, mauvaise réponse, temps dépassé, erreur) |
| `V9__evaluation_version.sql` | `evaluations.version` : verrou optimiste (`@Version`), pour qu'un « modifier » et un « publier » simultanés ne s'écrasent pas |
| `V10__jetons_revoques.sql` | jetons révoqués à la déconnexion (DS-14), purgés une fois expirés |
| `V11__emails_en_minuscules.sql` | emails en minuscules et sans espaces, unicité insensible à la casse |
| `V12__notifications_liens_et_index.sql` | notifications : liens vers l'évaluation et la copie, index du compteur et de la purge (DS-15) |
| `V13__mot_de_passe_modifie_le.sql` | date du dernier changement de mot de passe : les jetons émis avant sont refusés |
| `V14__mot_de_passe_provisoire.sql` | mot de passe provisoire attribué par l'administrateur, à changer avant tout autre usage (DS-16) |
| `V15__jetons_reinitialisation.sql` | liens de réinitialisation du mot de passe (empreinte SHA-256 seulement, usage unique, 30 min) |
| `V16__penalite_qcm.sql` | barème négatif optionnel des QCM et quiz : points retirés pour une mauvaise réponse (0 par défaut) |
| `V17__publication_resultats_programmee.sql` | date de publication automatique des résultats choisie par l'enseignant (DS-07, publication différée) |

## API

Toutes les routes sont préfixées par `/api`. Les routes autres que le login exigent l'en-tête
`Authorization: Bearer <jeton>` obtenu via `POST /api/auth/login`.

| Méthode | Route | Rôle | Description |
|---|---|---|---|
| POST | `/auth/login` | public | authentification, renvoie le jeton JWT |
| GET | `/auth/me` | connecté | profil de l'utilisateur connecté |
| POST | `/auth/logout` | connecté | révoque le jeton de cette session (V10, DS-14) |
| POST | `/auth/mot-de-passe-oublie` | public | envoie un lien de réinitialisation valable 30 min (V15) ; 202 que le compte existe ou non, au plus 3 e-mails par compte en 15 min |
| POST | `/auth/reinitialiser-mot-de-passe` | public | nouveau mot de passe depuis le lien (usage unique) ; coupe les sessions ouvertes ; 400 `JETON_INVALIDE` si le lien est inconnu, expiré ou remplacé |
| GET | `/profil` | connecté | identité en lecture seule (gérée par l'administrateur) |
| PUT | `/profil/mot-de-passe` | connecté | change son mot de passe (actuel exigé, échecs comptés avec la connexion) ; coupe les autres sessions et renvoie le nouveau jeton de celle-ci (`{token}`) |
| GET | `/notifications` | connecté | ses notifications, les plus récentes d'abord (DS-15) |
| GET | `/notifications/non-lues/nombre` | connecté | nombre de non lues (compteur de la barre latérale) |
| PUT | `/notifications/{id}/lire` | connecté | marque une notification comme lue |
| PUT | `/notifications/lire-tout` | connecté | marque toutes ses notifications comme lues |
| GET | `/etudiant/evaluations` | étudiant | évaluations de sa promotion et état de sa copie |
| GET | `/etudiant/evaluations/{id}` | étudiant | détail d'une évaluation (durée effective, durée par question du quiz, dates, état de la copie) ; 404 pour un brouillon, 403 hors de sa promotion |
| GET | `/promotions` | enseignant, admin | promotions et effectifs, année la plus récente d'abord (assistant de création, DS-03) |
| POST | `/enseignant/evaluations` | enseignant | crée un brouillon avec ses questions (DS-03) ; QCM et quiz : `penalite` facultative (barème négatif, entre 0 et les points de la question) |
| GET | `/enseignant/evaluations` | enseignant | ses évaluations, sans les questions, avec `nbCopies` et `nbAttendus` |
| GET | `/enseignant/evaluations/{id}` | enseignant (propriétaire) | détail avec questions, bonnes réponses, aménagements, `nbCopies` et `nbAttendus` |
| PUT | `/enseignant/evaluations/{id}` | enseignant (propriétaire) | modifie un brouillon (questions remplacées en bloc) |
| DELETE | `/enseignant/evaluations/{id}` | enseignant (propriétaire) | supprime un brouillon |
| POST | `/enseignant/evaluations/{id}/publier` | enseignant (propriétaire) | BROUILLON → PUBLIEE (DS-04) |
| POST | `/enseignant/evaluations/{id}/depublier` | enseignant (propriétaire) | PUBLIEE → BROUILLON tant qu'aucune copie n'existe (DS-04) |
| GET | `/enseignant/evaluations/{id}/amenagements` | enseignant (propriétaire) | étudiants attendus, durée standard, plage d'ouverture et tiers-temps de chacun ; `modifiable` = ce que le PUT / DELETE acceptera (DS-06) |
| PUT | `/enseignant/evaluations/{id}/amenagements/{etudiantId}` | enseignant (propriétaire) | tiers-temps ; corps `{ dureeMinutes }` (DS-06) |
| DELETE | `/enseignant/evaluations/{id}/amenagements/{etudiantId}` | enseignant (propriétaire) | supprime un tiers-temps |
| POST | `/enseignant/evaluations/{id}/resultats/publier` | enseignant (propriétaire) | rend notes et corrigés consultables et notifie les étudiants (DS-07) |
| PUT | `/enseignant/evaluations/{id}/resultats/programmation` | enseignant (propriétaire) | programme la publication automatique des résultats (date future, après la marge) ; 400 / 409 (V17, DS-07) |
| DELETE | `/enseignant/evaluations/{id}/resultats/programmation` | enseignant (propriétaire) | annule la publication programmée ; 409 si rien n'est programmé (DS-07) |
| POST | `/enseignant/evaluations/{id}/corrections-ia/generer` | enseignant (propriétaire) | lance la génération (asynchrone, 202) des propositions IA des questions ouvertes d'une évaluation terminée (DS-08) |
| GET | `/enseignant/corrections-ia` | enseignant | propositions de l'IA (filtres `evaluationId`, `statut`) et nombre de réponses encore sans proposition (DS-08) |
| PUT | `/enseignant/corrections-ia/{id}/valider` | enseignant (propriétaire) | retient la note de l'IA, ou `{ note }` ; si c'est la dernière note de la copie, elle passe EVALUE (DS-08) |
| PUT | `/enseignant/corrections-ia/{id}/rejeter` | enseignant (propriétaire) | rejette ; `{ note }` obligatoire (DS-08) |
| GET | `/enseignant/corrections-manuelles` | enseignant | réponses ouvertes sans proposition IA et pas encore notées, à noter à la main (filtre `evaluationId`) ; même critère que `enCours` (DS-08) |
| PUT | `/enseignant/reponses/{id}/note` | enseignant (propriétaire) | note une réponse sans proposition (modèle indisponible, code sans cas de test) (DS-08) |
| GET | `/enseignant/soumissions/{id}/copie` | enseignant (propriétaire) | copie complète : réponses, code, verdicts des cas de test (masqués compris), notes, historique des révisions ; `notesModifiables` (DS-08) |
| PUT | `/enseignant/reponses/{id}/revision` | enseignant (propriétaire) | revoit une note déjà posée (QCM, Judge0, IA, à la main) avant publication : `{ note, motif }`, motif obligatoire, tracé (V18) et montré à l'étudiant ; note finale recalculée ; 409 si résultats publiés ou réponse pas encore notée (DS-08) |
| GET | `/admin/utilisateurs` | admin | liste des comptes (filtres `role`, `actif`, `q`), jamais de mot de passe (DS-16) |
| POST | `/admin/utilisateurs` | admin | crée un compte ; renvoie un mot de passe provisoire, une seule fois (DS-16) |
| PUT | `/admin/utilisateurs/{id}` | admin | modifie un compte (jamais son rôle) ; un étudiant qui a des copies ne change plus de promotion (DS-16) |
| POST | `/admin/utilisateurs/{id}/desactiver` \| `/activer` | admin | désactive (effet immédiat sur ses jetons) ou réactive ; refusé pour son propre compte et le dernier administrateur actif (DS-16) |
| POST | `/admin/utilisateurs/{id}/reinitialiser-mot-de-passe` | admin | nouveau mot de passe provisoire, une seule fois ; coupe les sessions ouvertes du compte (V13, DS-16) ; le compte doit le changer à la connexion (V14 : 403 `MOT_DE_PASSE_A_CHANGER` hors `/auth` et `/profil`) |
| POST | `/admin/promotions` | admin | crée une promotion (DS-16) |
| PUT | `/admin/promotions/{id}` | admin | renomme une promotion ; 409 si doublon nom + année (DS-16) |
| DELETE | `/admin/promotions/{id}` | admin | supprime une promotion vide ; 409 si elle a des étudiants ou des évaluations (DS-16) |
| GET | `/enseignant/comparaison?ids=1,2,3` | enseignant | indicateurs sur 20 de 2 à 6 évaluations côte à côte, mêmes chiffres que les statistiques ; 400 / 403 / 404 / 409 (DS-19) |
| GET | `/admin/tableau-de-bord` | admin | comptes actifs par rôle, évaluations, activité sur 7 jours, services réellement connus (DS-18) |
| GET | `/admin/configuration` | admin | configuration réellement appliquée, en lecture seule, source de chaque valeur, jamais de secret (DS-18) |
| GET | `/admin/supervision` | admin | évaluations en cours de toute la plateforme et 20 dernières alertes ; à relire toutes les 30 s (DS-18) |
| GET | `/admin/statistiques` | admin | notes sur 20 des copies notées, taux de remise, détail par promotion ; indicateur `null` sans copie notée (DS-18) |
| POST | `/admin/etudiants/import` | admin | import CSV `nom;prenom;email;numEtudiant;promotion`, tout ou rien : 200, ou 422 avec les lignes en erreur (DS-16) |
| GET | `/enseignant/evaluations/{id}/statistiques` | enseignant (propriétaire) | moyenne, médiane, min, max, écart-type, taux de réussite, distribution et taux par question, sur 20 ; 409 avant l'ouverture (DS-09) |
| GET | `/enseignant/evaluations/{id}/resultats` | enseignant (propriétaire) | notes par étudiant attendu (absents compris), mêmes lignes que l'export CSV, sans e-mail ; `null` (jamais 0) sans note ; 409 avant l'ouverture (DS-09) |
| GET | `/enseignant/evaluations/{id}/export/pdf` | enseignant (propriétaire) | rapport PDF : indicateurs sur 20 et notes par étudiant, sans e-mail (DS-09) |
| GET | `/enseignant/evaluations/{id}/export/detail-csv` | enseignant | CSV du détail par question : une ligne par étudiant et par question (DS-09) |
| GET | `/enseignant/evaluations/{id}/export/csv` | enseignant (propriétaire) | résultats en CSV (UTF-8 BOM, « ; »), une ligne par étudiant attendu, absents compris (DS-09) |
| GET | `/enseignant/supervision/{id}` | enseignant (propriétaire) | avancement en lecture seule d'une évaluation ouverte : état de chaque étudiant, réponses données, alertes anti-triche ; à relire toutes les 15 s (DS-17) |
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

Diagrammes : DS-03 (création et modification), DS-04 (publication), DS-05 (statuts automatiques), DS-06 (tiers-temps)
et DS-07 (publication des résultats), dans [`docs/diagrammes-sequence`](../docs/diagrammes-sequence).

- **Brouillon d'abord** : toute évaluation créée est en `BROUILLON`. L'enseignant vient du jeton, jamais du corps de la
  requête, et ne voit que ses évaluations (403 sur celle d'un autre).
- **Promotions** : au moins une, choisie dans `GET /promotions` ; une promotion inconnue renvoie 404.
- **Modification et suppression** : uniquement en `BROUILLON` (sinon 409 `EVALUATION_NON_MODIFIABLE`). Pour corriger une
  évaluation publiée, il faut d'abord la dépublier, ce qui n'est possible que tant qu'aucune copie n'existe.
- **Règles des questions** : QCM = au moins 2 choix et au moins 1 correct (un seul si `estChoixMultiple = false`) ;
  quiz = règles du QCM + `dureeQuestion` > 0 ; code = au moins 1 cas de test ; points > 0. L'ordre est la position
  dans la liste. Une règle violée renvoie 400 (« Question 3 : … ») et rien n'est enregistré.
- **Cas de test** : l'**entrée est facultative** (un programme peut ne rien lire ; vide = absente) ; la **sortie attendue
  est obligatoire et non vide**, car Judge0 considère une sortie vide comme absente et un cas qui l'attendrait
  échouerait toujours. Un exercice de code est un **programme complet** : l'entrée arrive sur l'entrée standard, la
  sortie standard est comparée à la sortie attendue (espaces de fin de ligne ignorés).
- **Publication** : exige au moins une question, une `dateFermeture` future et une **plage d'ouverture assez longue** :
  la fin d'une épreuve est plafonnée à la fermeture, donc `max(durée standard, tiers-temps) ≤ fermeture − ouverture`
  (sinon 400). Le passage à `EN_COURS` puis `TERMINEE` est fait par `EvaluationStatutService` toutes les 60 s (une
  évaluation publiée déjà fermée va directement à `TERMINEE`). Cette tâche ne soumet aucune copie (DS-01, DS-11) et ne
  publie pas les résultats.
- **Tiers-temps** : la durée est au moins égale à la durée standard **et tient dans la plage d'ouverture** (sinon il
  serait sans effet : 400), l'étudiant est dans une promotion de l'évaluation, et l'aménagement est **verrouillé dès que
  l'étudiant a commencé sa copie** (409), car `TempsEpreuveService` le relit à chaque calcul. L'enregistrement
  **verrouille l'évaluation** (`FOR UPDATE`), comme la dépublication : le démarrage d'une copie prend le verrou partagé
  (`FOR SHARE`) et attend, un étudiant ne voit donc jamais sa durée changer pendant qu'il démarre.
- **Concurrence** : `Evaluation` porte un `@Version`. Deux écritures simultanées (modifier / publier) : la seconde reçoit
  409 `CONFLIT_CONCURRENT`.
- **Passage de statut atomique** : `UPDATE … WHERE statut = ancien` (`versioned`) ; seule la tâche qui change réellement
  la ligne déclenche `NotificationService.evaluationOuverte`, donc jamais de notification en double. Chaque évaluation est
  traitée dans sa propre transaction : un échec n'empêche pas les autres et annule statut et notifications ensemble.
- **Publication des résultats (DS-07)** : exige une évaluation `TERMINEE` dont la marge de tolérance (2 min après la
  fermeture) est écoulée, sinon 409 ; déjà publiés, 409. La bascule `resultatsPublies` est conditionnelle et atomique
  (deux clics ne notifient pas deux fois). Les copies pas encore notées ne bloquent pas : elles sont comptées « en
  attente » et leur étudiant est notifié à la validation de leur dernière note (`NotificationService.resultatPublie`).
- **Correction assistée par IA (DS-08)** : l'IA propose, l'enseignant décide. `CorrecteurIA` (interface) renvoie une note et
  une justification ; la note est toujours ramenée dans [0 ; barème] et aucune identité d'étudiant n'est transmise. Le
  fournisseur par défaut (`correction.ia.fournisseur = simule`) ne fait aucun appel réseau : c'est une démonstration, pas une
  correction pédagogique ; un connecteur vers un vrai modèle implémentera la même interface. La génération est asynchrone, en
  trois phases comme DS-12 (aucune transaction pendant l'appel au modèle) ; une indisponibilité du modèle laisse la réponse à
  noter à la main. Valider, rejeter et noter à la main verrouillent la copie (`FOR UPDATE`) : la dernière note pose
  `noteFinale`, passe la copie EVALUE et appelle `NotificationService.resultatPublie(copie)` (sans effet si les résultats ne
  sont pas publiés, jamais en double). 409 `CORRECTION_NON_MODIFIABLE` si la copie n'est plus en correction ou la proposition déjà traitée.
- **Administration (DS-16)** : un compte n'est jamais supprimé (copies, évaluations et notifications y sont rattachées), il est
  désactivé ; `AuthTokenFilter` relit le compte à chaque requête, donc les jetons d'un compte désactivé sont refusés aussitôt.
  Mots de passe provisoires : `SecureRandom`, 12 caractères sans ambiguïté, renvoyés une seule fois, stockés hachés, jamais
  journalisés. Garde-fous : on ne désactive ni son propre compte ni le dernier administrateur actif (les administrateurs actifs
  sont verrouillés `FOR UPDATE`). Import CSV : 500 lignes au plus, vérifié en entier avant toute création, une seule
  transaction, mots de passe hachés en parallèle ; les doublons sont cherchés dans le fichier et en base.
  409 `COMPTE_OPERATION_REFUSEE` pour les doublons et les opérations interdites.
- **Statistiques et export (DS-09)** : calculés à la demande (aucune table de statistiques), sur les seules copies notées
  (`EVALUE`), notes ramenées sur 20 (note / barème × 20). Réussite = 10/20 ; écart-type de la population, exigeant 2 copies ;
  sans copie notée, tout est `null` (jamais 0). Export CSV : cellules commençant par `= + - @` ou une tabulation préfixées d'une
  apostrophe (injection de formule dans le tableur), décimales à la virgule, `Cache-Control: no-store`, nom de fichier
  `resultats-{id}.csv` (jamais le titre). Fichier de données personnelles : réservé au propriétaire de l'évaluation.
- **Vue d'ensemble admin (DS-18)** : trois lectures seules, sans verrou, calculées à la demande (aucune table de statistiques), donc
  cohérentes avec les écrans de l'enseignant. `services` ne contient que ce que le serveur sait (base de données, connecteur IA configuré) :
  aucun état n'est inventé. Indicateurs `null` (jamais 0) sans copie notée.
- **Supervision (DS-17)** : lecture seule et sans verrou (superviser ne ralentit jamais les étudiants). Le « temps réel » est un
  sondage du frontend. Les réponses données sont comptées en trois requêtes groupées (QCM avec un choix coché, code et texte non
  vides) : une réponse vide enregistrée par la sauvegarde automatique ne compte pas. 409 avant l'ouverture. Aucun contenu de réponse ni note.
- **Notifications** : les services appellent l'interface `NotificationService` (3 événements), implémentée par
  `NotificationServiceEnBase` (DS-15, Mor). Sans elle, `NotificationParDefautConfig` fournirait une implémentation sans effet, déclarée en
  `@Bean @Fallback` : Spring ne l'injecte que si aucune autre implémentation existe, quel que soit l'ordre d'enregistrement
  des beans (`@ConditionalOnMissingBean` n'est fiable que dans une auto-configuration). `NotificationParDefautConfigTest`
  le vérifie dans les deux ordres.
- **Réponses de l'enseignant** : `EvaluationResponse` contient les bonnes réponses ; elle ne doit jamais être servie à un
  étudiant (l'étudiant reçoit `EvaluationEtudiantResponse`, sans question).

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

Côté enseignant et administrateur (DS-03 à DS-09, DS-16 à DS-18) :

| Service | Responsabilité |
|---|---|
| `EvaluationService` | création, modification, suppression, publication et dépublication d'une évaluation ; règles des questions |
| `AmenagementTempsService` | tiers-temps d'un étudiant (plage, verrou de l'évaluation, copie déjà commencée) |
| `EvaluationStatutService` | passage automatique `PUBLIEE → EN_COURS → TERMINEE`, appelé par `PassationScheduler` |
| `PublicationResultatsService` | publication des résultats d'une évaluation terminée |
| `PromotionService` | liste des promotions pour l'assistant de création |
| `GenerationCorrectionIAService` + `CorrectionIATache` | déclenchement asynchrone des propositions IA d'une évaluation terminée (DS-08) |
| `CorrectionIAService` | proposition, consultation, validation, rejet et note manuelle ; calcul de la note finale (DS-08) |
| `CorrecteurIA` / `CorrecteurIASimule` | connecteur du modèle de langage (simulé par défaut) |
| `AdminVueEnsembleService` | tableau de bord, supervision globale et statistiques globales de l'administrateur (DS-18) |
| `SupervisionService` | avancement et alertes d'une évaluation ouverte (DS-17) |
| `StatistiquesEvaluationService` | statistiques d'une évaluation sur 20 (DS-09) |
| `ExportResultatsService` | export CSV des résultats, cellules protégées contre l'injection de formule (DS-09) |
| `AdminComptesService` | comptes, promotions, désactivation et mots de passe provisoires (DS-16) |
| `ImportEtudiantsService` | import CSV d'étudiants, tout ou rien (DS-16) |
| `GenerateurMotDePasse` | mots de passe provisoires (`SecureRandom`) |
| `NotificationService` (interface) | événements de notification ; implémentation par défaut sans effet |
| `NotificationServiceEnBase` | notifications enregistrées en base, dans la transaction de l'événement (DS-15) |
| `MesNotificationsService` | liste, compteur, marquage comme lues, purge des lues de plus de 6 mois |

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
- **Module enseignant** : `EvaluationServiceTest`, `AmenagementTempsServiceTest`, `EvaluationStatutServiceTest`,
  `PublicationResultatsServiceTest` et `PromotionServiceTest` (unitaires, horloge figée) ; `EvaluationControllerIntegrationTest`
  (parcours HTTP complet, sécurité par rôle, plage et tiers-temps, cas de test) ; `EvaluationStatutIntegrationTest` (transitions
  réelles, deux passages simultanés, une seule notification) ; `PublicationResultatsIntegrationTest` (de « en attente » à
  « publiés » côté étudiant, double publication) ; `EnseignantPostgresIntegrationTest` (sur PostgreSQL : mises à jour
  conditionnelles, deux passages simultanés, publication atomique, et un tiers-temps qui **attend** le verrou partagé d'un
  démarrage de copie).
- **Test PostgreSQL** (`MigrationPostgresIntegrationTest`, Testcontainers) : applique la migration Flyway sur un vrai
  PostgreSQL et rejoue une passation. Il nécessite Docker ; sans Docker, il est ignoré localement mais s'exécute en CI.

Utiliser `mvn clean test` plutôt qu'une compilation incrémentale (un fichier de migration renommé resterait dans
`target/classes`). Si Mockito échoue avec « Could not self-attach to current VM using external process » (observé sous
Windows), lancer avec `JAVA_TOOL_OPTIONS=-Djdk.attach.allowAttachSelf=true`.

La CI GitHub Actions (`.github/workflows/ci.yml`) lance les tests du backend, le lint (oxlint), les tests et le build
du frontend, la collection Postman contre le vrai backend (base neuve, profil `h2-dev`) et les tests de bout en bout
Playwright. Elle s'exécute à chaque push sur `main`, `feature/**`, `feat/**`, `fix/**`, `refactor/**` et `docs/**`, ainsi qu'à chaque pull request vers `main`.
