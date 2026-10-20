# DS-03 — Création, édition et publication d'une évaluation (Enseignant)

> Auteur : Aminou NIANG — statut : **à valider par Mor avant codage**.
> Classes concernées : `EvaluationController`, `EvaluationService`, `AmenagementTempsService`, `EvaluationRepository`, `PromotionRepository`, `QuestionRepository`, `AmenagementTempsRepository`.
> Les diagrammes DS-01 (soumission) et DS-02 (auto-save) sont ceux de Mor.

## Décisions de conception (à confirmer)

| # | Décision | Pourquoi |
|---|---|---|
| 1 | L'assistant 3 étapes garde son état **côté front** (brouillon `sessionStorage`, déjà en place). Un seul `POST` crée l'évaluation avec ses questions. | Évite des brouillons à moitié remplis en base et un endpoint par étape. |
| 2 | Toute évaluation créée est en `BROUILLON`. `PUBLIEE` se déclenche uniquement par `POST /{id}/publier`. | Cycle de vie du CDC : BROUILLON → PUBLIEE → EN_COURS → TERMINEE. |
| 3 | Modification et suppression possibles **uniquement en `BROUILLON`**. Sinon `409 Conflict`. | Une copie peut exister dès la publication : on ne change plus les questions. |
| 4 | `PUBLIEE → EN_COURS → TERMINEE` est fait par une tâche planifiée (pas par l'enseignant). | `SoumissionService` teste déjà `dateOuverture` / `dateFermeture` ; le statut doit rester cohérent avec elles. |
| 5 | Le propriétaire est lu dans le JWT (`Authentication`), jamais dans le corps de la requête. Un enseignant ne touche que ses évaluations (`403` sinon). | Sécurité : aucun `enseignantId` fourni par le client. |
| 6 | La validation métier des questions est dans `EvaluationService`, par type (voir tableau ci-dessous). Les contraintes simples (`@NotBlank`, `@Min`) sont dans les DTO. | Un seul endroit à tester. |

Règles de validation par type de question :

| Type | Règles |
|---|---|
| `QCM` | ≥ 2 choix ; ≥ 1 choix correct ; si `estChoixMultiple = false`, exactement 1 choix correct |
| `QUIZ_CHRONOMETREE` | règles du QCM + `dureeQuestion` > 0 |
| `CODE` | ≥ 1 cas de test ; chaque cas a `entree` et `sortieAttendue` |
| `OUVERTE` | `enonce` non vide, rien d'autre |
| toutes | `points` > 0 ; `ordre` unique dans l'évaluation |

## 1. Création (brouillon)

```mermaid
sequenceDiagram
    autonumber
    actor Ens as Enseignant
    participant FE as Front (assistant 3 étapes)
    participant C as EvaluationController
    participant S as EvaluationService
    participant PR as PromotionRepository
    participant ER as EvaluationRepository

    Ens->>FE: Étape 1 infos, étape 2 questions, étape 3 prévisualisation
    Note over FE: état conservé en sessionStorage
    Ens->>FE: Clique « Enregistrer le brouillon »
    FE->>C: POST /api/enseignant/evaluations (CreateEvaluationRequest) + JWT
    Note over C: @PreAuthorize("hasRole('ENSEIGNANT')")
    C->>C: @Valid sur le DTO
    alt DTO invalide
        C-->>FE: 400 Bad Request (champs en erreur)
    else DTO valide
        C->>S: creer(request, authentication.getName())
        S->>S: dateFermeture > dateOuverture ?
        S->>PR: findAllById(promotionIds)
        PR-->>S: promotions
        alt promotion inconnue
            S-->>C: RessourceNonTrouveeException
            C-->>FE: 404 Not Found
        else règles des questions violées
            S-->>C: IllegalArgumentException
            C-->>FE: 400 Bad Request (message par question)
        else tout est valide
            S->>S: construire Evaluation (statut = BROUILLON)<br/>et Question* (QCM, Quiz, Code, Ouverte)
            S->>ER: save(evaluation) (cascade questions et choix)
            ER-->>S: evaluation (id)
            S-->>C: EvaluationResponse
            C-->>FE: 201 Created + Location /api/enseignant/evaluations/{id}
            FE->>FE: vider le brouillon sessionStorage
            FE-->>Ens: redirection vers la liste des évaluations
        end
    end
```

## 2. Modification d'un brouillon

```mermaid
sequenceDiagram
    autonumber
    actor Ens as Enseignant
    participant FE as Front
    participant C as EvaluationController
    participant S as EvaluationService
    participant ER as EvaluationRepository

    Ens->>FE: Modifier une évaluation
    FE->>C: GET /api/enseignant/evaluations/{id}
    C->>S: details(id, email)
    S->>ER: findById(id)
    ER-->>S: evaluation
    S->>S: evaluation.enseignant == utilisateur connecté ?
    alt autre enseignant
        S-->>C: AccesRefuseException
        C-->>FE: 403 Forbidden
    else propriétaire
        S-->>C: EvaluationResponse
        C-->>FE: 200 OK (formulaire pré-rempli)
    end
    Ens->>FE: Enregistre
    FE->>C: PUT /api/enseignant/evaluations/{id} (CreateEvaluationRequest)
    C->>S: modifier(id, request, email)
    S->>ER: findById(id)
    alt statut ≠ BROUILLON
        S-->>C: EvaluationNonModifiableException
        C-->>FE: 409 Conflict
    else statut = BROUILLON
        S->>S: mêmes validations que la création
        S->>S: remplacer les questions (orphanRemoval)
        S->>ER: save(evaluation)
        S-->>C: EvaluationResponse
        C-->>FE: 200 OK
    end
```

## 3. Publication

```mermaid
sequenceDiagram
    autonumber
    actor Ens as Enseignant
    participant FE as Front
    participant C as EvaluationController
    participant S as EvaluationService
    participant ER as EvaluationRepository

    Ens->>FE: Clique « Publier »
    FE->>C: POST /api/enseignant/evaluations/{id}/publier
    C->>S: publier(id, email)
    S->>ER: findById(id)
    S->>S: propriétaire ? statut = BROUILLON ?
    alt non propriétaire
        S-->>C: AccesRefuseException
        C-->>FE: 403 Forbidden
    else déjà publiée
        S-->>C: EvaluationNonModifiableException
        C-->>FE: 409 Conflict
    else sans question ou dateOuverture dépassée
        S-->>C: IllegalArgumentException
        C-->>FE: 400 Bad Request
    else publiable
        S->>S: statut = PUBLIEE
        S->>ER: save(evaluation)
        S-->>C: EvaluationResponse
        C-->>FE: 200 OK
        Note over S: NotificationService (module de Mor) notifie les<br/>étudiants des promotions lors du passage à EN_COURS
    end
```

## 4. Passage automatique `PUBLIEE → EN_COURS → TERMINEE`

```mermaid
sequenceDiagram
    autonumber
    participant J as Tâche planifiée (chaque minute)
    participant S as EvaluationService
    participant ER as EvaluationRepository
    participant N as NotificationService

    J->>S: mettreAJourStatuts()
    S->>ER: PUBLIEE dont dateOuverture ≤ maintenant
    ER-->>S: évaluations à ouvrir
    loop chaque évaluation
        S->>S: statut = EN_COURS
        S->>N: notifier(EVALUATION_OUVERTE, promotions)
    end
    S->>ER: EN_COURS dont dateFermeture ≤ maintenant
    ER-->>S: évaluations à clôturer
    loop chaque évaluation
        S->>S: statut = TERMINEE
    end
    S->>ER: saveAll(...)
```

Le passage `TERMINEE` ne clôt pas les copies encore `EN_COURS` : c'est le rôle de DS-01 (soumission automatique à l'expiration).

## 5. Aménagement de temps (tiers-temps)

```mermaid
sequenceDiagram
    autonumber
    actor Ens as Enseignant
    participant FE as Front
    participant C as EvaluationController
    participant A as AmenagementTempsService
    participant ER as EvaluationRepository
    participant UR as EtudiantRepository
    participant AR as AmenagementTempsRepository

    Ens->>FE: Définit une durée personnalisée pour un étudiant
    FE->>C: PUT /api/enseignant/evaluations/{id}/amenagements (AmenagementRequestDTO)
    C->>A: definir(id, etudiantId, dureeMinutes, email)
    A->>ER: findById(id)
    A->>A: propriétaire ? statut ≠ TERMINEE ? dureeMinutes ≥ dureeStandard ?
    alt règle violée
        A-->>C: exception (403, 409 ou 400)
        C-->>FE: code d'erreur correspondant
    else valide
        A->>UR: findById(etudiantId) (doit être dans une promotion de l'évaluation)
        A->>AR: save ou mise à jour de (etudiant, évaluation)
        A-->>C: AmenagementResponse
        C-->>FE: 200 OK
    end
    Note over A: DS-01 lit cet aménagement au démarrage de la copie<br/>pour calculer l'heure de clôture de l'étudiant
```

## Endpoints résultant des diagrammes

| Méthode | Route | Rôle | Succès | Erreurs |
|---|---|---|---|---|
| `POST` | `/api/enseignant/evaluations` | créer un brouillon | 201 | 400, 404 |
| `GET` | `/api/enseignant/evaluations` | mes évaluations | 200 | |
| `GET` | `/api/enseignant/evaluations/{id}` | détail | 200 | 403, 404 |
| `PUT` | `/api/enseignant/evaluations/{id}` | modifier un brouillon | 200 | 400, 403, 404, 409 |
| `DELETE` | `/api/enseignant/evaluations/{id}` | supprimer un brouillon | 204 | 403, 404, 409 |
| `POST` | `/api/enseignant/evaluations/{id}/publier` | BROUILLON → PUBLIEE | 200 | 400, 403, 404, 409 |
| `PUT` | `/api/enseignant/evaluations/{id}/amenagements` | tiers-temps | 200 | 400, 403, 404, 409 |

## Nouvelles classes à créer (selon ces diagrammes)

- DTO : `CreateEvaluationRequest`, `QuestionRequestDTO`, `ChoixDTO`, `CasDeTestDTO`, `AmenagementRequestDTO`, `EvaluationResponse`.
- Exception : `EvaluationNonModifiableException` (→ 409 dans `GlobalExceptionHandler`, fichier de Mor).
- Tâche planifiée : `@EnableScheduling` à ajouter (config de Mor : à valider avec lui).

## Questions pour Mor

1. Routes : on garde `/api/enseignant/evaluations` (comme dans ton message) alors que `SoumissionController` utilise `/soumissions` ?
2. OK pour ajouter `EvaluationNonModifiableException` dans `GlobalExceptionHandler` et `@EnableScheduling` ?
3. La notification `EVALUATION_OUVERTE` : tu exposes une méthode `NotificationService.notifier(type, promotions)` ou on se met d'accord sur la signature ?
