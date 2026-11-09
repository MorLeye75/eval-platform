# Plateforme d'évaluation EPT — Frontend

Application React 19 + TypeScript + Vite (Tailwind, composants shadcn/ui, React Router, React Query, axios).

## Démarrage

```bash
npm install
npm run dev        # http://localhost:5173
```

Le backend doit tourner sur `http://localhost:8080` (voir `eval-platform-backend/README.md`, profil `h2-dev` pour
démarrer sans base). Une autre adresse se configure avec la variable `VITE_API_URL` (par défaut
`http://localhost:8080/api`).

Comptes de démonstration : `etudiant@ept.sn` / `etudiant123`, `prof@ept.sn` / `prof123`, `admin@ept.sn` / `admin123`.

## Scripts

| Commande | Rôle |
|---|---|
| `npm run dev` | serveur de développement |
| `npm run build` | vérification TypeScript puis build de production |
| `npm run lint` | analyse statique (oxlint) |
| `npm test` | tests unitaires (Vitest) |
| `npm run test:e2e` | tests de bout en bout (Playwright), sans aucune simulation |

### Tests de bout en bout

`e2e/parcours-etudiant.e2e.ts` fait passer le parcours étudiant dans un vrai navigateur, contre le vrai backend et
une vraie base (H2 neuve à chaque exécution, données de démonstration) : connexion, QCM sauvegardé puis retrouvé après
rechargement et remis, résultats non publiés, quiz imposé par le serveur, code saisi avec son langage puis remis,
cloisonnement des rôles. Playwright démarre lui-même le backend (port 18082) et la version de production du front
(port 5174) : l'environnement de développement (8080, 5173) peut rester lancé.

```bash
cd ../eval-platform-backend && mvn package -DskipTests   # le jar du backend
cd ../eval-platform-frontend && npx playwright install chromium   # une seule fois
npm run test:e2e
```

L'éditeur de code (Monaco) est livré avec l'application, sans CDN : le scénario du code bloque d'ailleurs jsDelivr
pour le prouver, et celui du QCM vérifie qu'une épreuve sans code ne télécharge pas Monaco.

## État du branchement sur l'API

| Écran | Source des données |
|---|---|
| Connexion, session (`/auth/me`) | API |
| Espace étudiant : accueil, liste et détail des évaluations | API (`GET /etudiant/evaluations`, `GET /etudiant/performances`) |
| Passation : QCM, question ouverte, code, épreuve mixte (écran unique) | API : démarrage, sauvegarde automatique, anti-triche, remise |
| Quiz chronométré | API : question en cours et temps imposés par le serveur (`POST /soumissions/{id}/quiz/suivante`) |
| Résultats d'une copie, tableau de bord des performances | API (`GET /soumissions/{id}/resultats`, `GET /etudiant/performances`) |
| Notifications, profil | simulés (à brancher, voir DS-15) |
| Espaces enseignant et administrateur | simulés (`src/mocks`), module d'Aminou |

## Passation d'une épreuve

Les règles suivent les diagrammes de séquence de [`docs/diagrammes-sequence`](../docs/diagrammes-sequence)
(DS-01 soumission, DS-02 sauvegarde automatique, DS-10 démarrage et reprise).

| Fichier | Rôle |
|---|---|
| `src/hooks/usePassation.ts` | démarre ou reprend la copie, chrono sur le temps restant du serveur, soumission |
| `src/hooks/useAutoSave.ts` | sauvegarde toutes les 30 s, tampon `localStorage` hors ligne, nouvel essai à la reconnexion |
| `src/hooks/useExamTimer.ts` | compte à rebours sans dérive |
| `src/lib/passation.ts` | appels API et lecture des erreurs (codes 409) |

Principes :

- **Le serveur fait foi** : questions, réponses enregistrées et temps restant viennent de lui ; le chrono du navigateur
  n'est qu'un affichage, le serveur refuse toute action hors délai.
- **Version des réponses** : chaque sauvegarde envoie la version sur laquelle elle a été saisie. Si la copie a été
  enregistrée entre-temps depuis un autre onglet ou appareil (409 `VERSION_OBSOLETE`), les réponses locales sont mises
  de côté, la copie est rechargée et un bandeau prévient l'étudiant.
- **Hors ligne** : les réponses non confirmées restent dans le `localStorage` avec leur version. Au rechargement, elles
  ne sont restaurées que si le serveur n'a rien reçu de plus récent ; sinon elles sont gardées de côté, jamais
  détruites avant la remise de la copie.
- **Soumission** : une dernière sauvegarde part juste avant. Si elle échoue, la soumission manuelle est suspendue ; à la
  fin du chrono, la copie est soumise quand même avec les réponses déjà enregistrées.
- **Codes 409** : `TEMPS_EXPIRE` et `SOUMISSION_VERROUILLEE` mènent à l'écran d'expiration ; `VERSION_OBSOLETE`
  recharge la copie.
