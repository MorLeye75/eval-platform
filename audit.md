# Audit — eval-platform

> Date : 11/10/2026 — branche `refactor/revue-globale-plateforme` (commit `a7d8a19`)
>
> État : `tsc -b` ✅ aucune erreur · `vite build` ✅ · `oxlint` : 6 avertissements `only-export-components` (sans gravité).
> Le projet est encore en mode maquette : seul `/auth/login` appelle l'API, tout le reste lit `src/mocks/`.

Légende des priorités : 🔴 bug actuel · 🟠 sécurité / intégrité · 🟡 dette technique · 🔵 mineur

---

## 🔴 1. Effets de bord dans les fonctions de mise à jour d'état (question sautée, `onExpire` appelé deux fois)

**Fichiers**
- `src/pages/etudiant/passation/PassationQuiz.tsx` (chrono par question, l. 68-79)
- `src/hooks/useExamTimer.ts` (l. 23-30)
- `src/hooks/useAntiCheat.ts` (l. 24-28)

**Problème**
`questionSuivante()`, `onExpire()` et `onVisibilityChange()` sont appelés **à l'intérieur** d'une fonction passée à `setState(prev => …)`. En `StrictMode` (dev), React exécute ces fonctions deux fois :
- Quiz : `setIndexCourant(i => i + 1)` s'applique deux fois, donc **une question est sautée** ;
- `useExamTimer` : `navigate('/expiration')` est appelé deux fois ;
- `useAntiCheat` : le callback est notifié deux fois par sortie d'onglet (doublons vers le backend une fois branché).

Une fonction de mise à jour doit rester pure.

**Correction** : décrémenter dans l'intervalle, réagir à `0` dans un effet séparé.

```ts
// useAntiCheat.ts
function handleVisibilite() {
  if (!document.hidden) return
  setChangementsOnglet((prev) => prev + 1)
}
// puis, si besoin du callback :
useEffect(() => {
  if (changementsOnglet > 0) onVisibilityChangeRef.current?.(changementsOnglet)
}, [changementsOnglet])
```

Pour le Quiz et le timer, voir le correctif n°2, qui règle les deux problèmes en même temps.

---

## 🔴 2. Les chronomètres ralentissent ou se figent quand l'étudiant interagit

**Fichiers**
- `src/hooks/useExamTimer.ts` : `onExpire` est dans les dépendances de l'effet ;
- `PassationQCM.tsx`, `PassationCode.tsx`, `PassationOuverte.tsx`, `PassationQuiz.tsx` : `onExpire: () => navigate(...)` est une fonction recréée à chaque rendu ;
- `PassationQuiz.tsx` : `questionSuivante` dépend de `reponses`, et l'effet du chrono dépend de `questionSuivante`.

**Problème**
À chaque rendu (clic sur une réponse, frappe au clavier…), l'intervalle est détruit puis recréé et le compteur de la seconde en cours repart de zéro. Dans le Quiz, un étudiant qui clique plus vite qu'une fois par seconde **bloque le chrono**. De plus, un compteur décrémenté de 1 par `setInterval` dérive quand l'onglet est en arrière-plan (les navigateurs ralentissent les timers).

**Correction** : calculer le temps restant à partir d'une heure de fin, et passer `onExpire` par une ref.

```ts
// useExamTimer.ts
export function useExamTimer({ dureeMinutes, onExpire, enabled = true }: UseExamTimerOptions) {
  const [finA, setFinA] = useState<number | null>(null)
  const [tempsRestant, setTempsRestant] = useState(dureeMinutes * 60)
  const onExpireRef = useRef(onExpire)
  useEffect(() => { onExpireRef.current = onExpire })

  // Fixe l'heure de fin une seule fois, au démarrage
  useEffect(() => {
    if (enabled && finA === null) setFinA(Date.now() + dureeMinutes * 60_000)
  }, [enabled, finA, dureeMinutes])

  useEffect(() => {
    if (finA === null) return
    const tick = () => setTempsRestant(Math.max(0, Math.round((finA - Date.now()) / 1000)))
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [finA])

  useEffect(() => {
    if (finA !== null && tempsRestant === 0) onExpireRef.current()
  }, [finA, tempsRestant])
  // ...
}
```

Pour le Quiz, appliquer le même principe au chrono par question : stocker `finQuestionA` (réinitialisé quand `indexCourant` change), et appeler `questionSuivante` depuis un `useEffect` quand le temps atteint 0. Retirer `reponses` des dépendances de l'intervalle, par exemple en lisant les réponses via une ref.

> Quand le backend sera branché, `finA` devra être calculé à partir de la `dateDebut` de la `Soumission` renvoyée par le serveur, pour que le chrono survive à un rafraîchissement.

---

## 🔴 3. Échec de connexion : page rechargée et message d'erreur perdu

**Fichier** : `src/lib/axios.ts` (l. 18-27)

**Problème**
L'intercepteur redirige vers `/login` avec `window.location.href` sur **toute** réponse 401, y compris celle de `POST /auth/login` en cas de mauvais identifiants (comportement habituel de Spring Security). La page se recharge et « Email ou mot de passe incorrect » ne s'affiche jamais.

**Correction**

```ts
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const estLogin = error.config?.url?.includes("/auth/login")
    if (error.response?.status === 401 && !estLogin) {
      localStorage.removeItem("token")
      localStorage.removeItem("user")
      window.location.href = "/login"
    }
    return Promise.reject(error)
  }
)
```

Dans `src/components/login-form.tsx`, distinguer aussi les erreurs : 401 → identifiants incorrects, pas de réponse → serveur injoignable, autre → erreur générique.

---

## 🔴 4. Retour arrière ou F5 : l'examen redémarre à zéro

**Fichiers** : les 4 pages `src/pages/etudiant/passation/Passation*.tsx`

**Problème**
- `navigate('/examen/:id/confirmation')` et `navigate('/examen/:id/expiration')` ajoutent une entrée à l'historique. Le bouton « Retour » ramène sur l'examen avec un chrono plein et des réponses vides.
- Un rafraîchissement de page fait perdre toutes les réponses et remet le chrono à la durée totale.

**Correction**
1. Ajouter `{ replace: true }` à tous les `navigate` de fin d'examen :
   ```ts
   navigate(`/examen/${id}/confirmation`, { replace: true, state: { ... } })
   ```
2. En attendant le backend, conserver les réponses et l'heure de fin dans `sessionStorage` (clé `examen-${id}`) et les relire à l'initialisation.
3. Côté backend : à l'ouverture, créer ou reprendre la `Soumission` (statut `EN_COURS`) et refuser l'accès si elle est déjà `SOUMIS`.

---

## 🔴 5. Résultats : les questions ouvertes ou de code obtiennent tous les points

**Fichier** : `src/pages/etudiant/ResultatsDetail.tsx` (l. 219-226)

**Problème**
Pour une question sans `choix` (OUVERTE, CODE), on a `bonnesReponses = []` et `reponsesEtudiant = []`, donc `toutJuste === true` : la question est affichée comme **réussie avec tous les points**. Plus généralement, la note par question est recalculée côté client et peut contredire `r.pointsObtenus`.

**Correction**
- Court terme : n'appliquer ce calcul qu'aux questions `type === "QCM"` ; pour les autres, afficher la note fournie par les données.
- Cible : utiliser `ReponseEtudiant.noteObtenue` renvoyé par le backend et supprimer tout calcul de note dans le front.

```ts
const pointsObtenus =
  q.type === "QCM" ? (toutJuste ? q.points : 0) : (r.notesParQuestion?.[q.id] ?? 0)
```

---

## 🔴 6. Le nombre de questions traitées est faux dans le Quiz

**Fichier** : `src/pages/etudiant/passation/PassationQuiz.tsx` (l. 51)

**Problème**
`Object.keys(reponses).length` compte aussi une question cochée puis entièrement décochée (tableau vide).

**Correction** (même règle que dans PassationQCM)
```ts
const nbTraitees = Object.values(reponses).filter((r) => r.length > 0).length
```

---

## 🔴 7. Données mock incohérentes

**Fichier** : `src/mocks/evaluations.ts` (évaluation `id: 3`)

**Problème** : `type: "OUVERTE"` alors que ses questions sont des QCM avec `choix`. Une fois passée en `disponible`, elle redirigerait vers la mauvaise page de passation.

**Correction** : passer `type: "QCM"` (ou remplacer les questions par des questions ouvertes).

---

## 🟠 8. Les bonnes réponses sont exposées dans le bundle étudiant

**Fichiers** : `src/mocks/evaluations.ts`, les pages de passation, `src/types/index.ts`

**Problème** : les pages de passation chargent des questions qui contiennent `estCorrect` et les cas de test `estMasque: true` avec leur `sortieAttendue`. Une fois l'API branchée, si le backend renvoie la même structure, un étudiant peut lire les réponses dans les DevTools.

**Correction**
- Créer des types dédiés à la passation, sans champs sensibles :
  ```ts
  export type ChoixPassation = Omit<ChoixReponse, "estCorrect">
  export type CasTestPassation = Omit<CasTest, "estMasque"> // uniquement les cas visibles
  ```
- Côté backend : un DTO étudiant distinct ; ne jamais sérialiser `estCorrect` ni les cas masqués avant la publication des résultats.

---

## 🟠 9. Authentification et rôle stockés côté client

**Fichiers** : `src/context/AuthContext.tsx`, `src/lib/axios.ts`

**Problèmes**
- Le rôle est relu depuis `localStorage` : modifier `user.role` suffit à afficher l'interface admin.
- L'expiration du JWT n'est pas vérifiée au démarrage ; un token expiré laisse l'interface « connectée » jusqu'au premier appel API.
- Le token est stocké dans `localStorage` et donc lisible par un script injecté (XSS).

**Corrections**
- Backend : vérifier le rôle sur **chaque** endpoint (`@PreAuthorize`). Le `ProtectedRoute` du front n'est qu'un confort d'interface.
- Front : au démarrage, décoder le champ `exp` du JWT et déconnecter s'il est dépassé, ou appeler `GET /auth/me` et utiliser le rôle qu'il renvoie.
- Si possible : passer à un cookie `HttpOnly` + `SameSite` émis par le backend.

---

## 🟠 10. Messages trompeurs pour l'étudiant

| Message affiché | Réalité | Fichier |
|---|---|---|
| « sortie de la page détectée **et enregistrée** » | rien n'est envoyé au serveur | `Passation*.tsx` |
| « Sauvegardé à HH:MM » | l'heure est affichée **avant** la sauvegarde, qui n'est qu'un `console.log` | `src/hooks/useAutoSave.ts` (l. 31-41) |
| « Votre copie a été soumise automatiquement » | aucune soumission | `ExpirationScreen.tsx` |
| « Votre copie a bien été enregistrée » | aucune soumission (`TODO backend`) | `ConfirmationSoumission.tsx` |

**Correction**
- `useAutoSave` : mettre à jour `derniereSauvegarde` **après** un `onSave` réussi (rendre `onSave` asynchrone et afficher une erreur en cas d'échec) ; ne pas sauvegarder si les données n'ont pas changé.
- Brancher `POST /api/soumissions/{id}/soumettre` (confirmation et expiration) et `POST /api/soumissions/{id}/alertes` (anti-triche) avant toute démo réelle, ou reformuler les messages d'ici là.

---

## 🟠 11. Le temps limite doit être imposé par le serveur

Le chronomètre du front ne sert qu'à l'affichage. Le backend doit :
- enregistrer `dateDebut` à l'ouverture de la soumission ;
- refuser toute sauvegarde ou soumission après `dateDebut + duree` (avec une petite tolérance) ;
- soumettre automatiquement les copies `EN_COURS` expirées (tâche planifiée).

---

## 🟡 12. Deux modèles de données parallèles

**Fichiers** : `src/types/index.ts` (contrat backend) vs `src/mocks/evaluations.ts`

| Concept | `types/index.ts` | `mocks/evaluations.ts` |
|---|---|---|
| Type quiz | `QUIZ_CHRONOMETREE` | `QUIZ` |
| Cas de test | `casTests` | `casDeTests` |
| Dates | `dateOuverture` / `dateFermeture` (ISO) | `dateLimite` (texte formaté) |
| Statut | `PUBLIEE`, `EN_COURS`… | `disponible`, `a_venir`, `terminee` |

Toutes les pages étudiant utilisent les types mock : le branchement de l'API demandera de les réécrire.

**Correction** : réécrire les mocks avec les types de `src/types/index.ts`, formater les dates à l'affichage (`toLocaleDateString("fr-FR")`) et dériver le statut « disponible / à venir / terminée » d'une fonction utilitaire à partir des dates.

---

## 🟡 13. React Query installé mais non utilisé

**Correction** : créer une couche `src/api/` avec des hooks (`useEvaluations()`, `useEvaluation(id)`, `useSoumettre()`…) qui renvoient les mocks pour l'instant. Le jour où le backend est prêt, seuls ces hooks changent, pas les pages. Gérer les états `isLoading` / `isError` dans les pages.

---

## 🟡 14. Duplication dans les pages de passation

Les 4 pages `Passation*.tsx` (300 à 400 lignes chacune) répètent :
- `formatTemps` (×4), alors que `useExamTimer` renvoie déjà `tempsRestantFormate`, qui n'est pas utilisé ;
- la carte « évaluation introuvable » ;
- le bandeau anti-triche ;
- la barre timer + sauvegarde ;
- la grille de navigation entre questions ;
- la modale de confirmation de soumission.

**Correction** : extraire dans `src/components/examen/` les composants `ExamNotFound`, `ExamTopBar`, `AntiCheatBanner`, `QuestionNav` et `SubmitDialog`, et utiliser `tempsRestantFormate`.

---

## 🟡 15. Sidebars, layouts et redirections par rôle dupliqués

- `app-sidebar.tsx`, `AdminSidebar.tsx` et `EnseignantSidebar.tsx` ne diffèrent que par la liste des menus, l'icône et le libellé. On peut les remplacer par un seul composant `RoleSidebar({ menuItems, titre, icone })`. Même chose pour les trois layouts.
- **Bug lié** : la sidebar étudiant fait une comparaison stricte du chemin (`location.pathname === item.url`), donc « Mes évaluations » n'est pas surligné sur `/etudiant/evaluations/3` ni sur `/etudiant/resultats/3`. Utiliser `startsWith`, comme dans la sidebar admin.
- La redirection selon le rôle est écrite 4 fois (`AppRouter` / `IndexRedirect`, `ProtectedRoute`, `LoginPage`, `login-form`). Créer une fonction dans `src/lib/` :
  ```ts
  export function accueilPour(role: Role) {
    return role === "ADMIN" ? "/admin" : role === "ENSEIGNANT" ? "/enseignant" : "/etudiant/dashboard"
  }
  ```

---

## 🔵 16. Points mineurs

| # | Problème | Fichier | Correction |
|---|---|---|---|
| a | Les résultats d'exécution ne sont pas effacés quand le code change (badge « Réussi » sur du code modifié) | `PassationCode.tsx` | Vider `resultats[question.id]` dans `changerCode` et `changerLangage` |
| b | `setTimeout(navigate, 2500)` jamais annulé si l'utilisateur quitte la page | `ResetPasswordPage.tsx` l. 57 | Le placer dans un `useEffect` avec `clearTimeout` |
| c | Boutons « Nouvel utilisateur », « Modifier » et « Supprimer » sans `onClick` | `GestionComptes.tsx` | Les désactiver, ou les relier à une modale ou une page placeholder |
| d | `console.log` restants | `ParametresSysteme.tsx` l. 69, `PassationQuiz.tsx` l. 48, `useAutoSave.ts` l. 40 | Supprimer |
| e | Faute de frappe dans un nom de type | `types/index.ts` : `AlerteAntTriche` | Renommer en `AlerteAntiTriche` |
| f | Commentaire obsolète « ADDITIONS … à fusionner » | `types/index.ts` | Supprimer |
| g | Deux bibliothèques d'icônes (lucide + `@phosphor-icons` dans 7 composants `ui/`) | `components/ui/*` | Garder lucide seulement |
| h | `shadcn` (outil CLI) dans `dependencies` | `package.json` | Passer en `devDependencies` (vérifier l'import de `shadcn/tailwind.css` dans `index.css`) |
| i | Fichiers non utilisés | `src/assets/hero.png`, `react.svg`, `vite.svg` | Supprimer |
| j | README encore celui du template Vite | `README.md` | Décrire le projet, l'installation, `.env` (`VITE_API_URL`) et les rôles |
| k | Avertissements `only-export-components` (6) | `badge`, `button`, `tabs`, `sidebar`, `AdminWidgets`, `AuthContext` | Déplacer `useAuth` et `TONE_CLS` dans des fichiers séparés (les fichiers `ui/` générés peuvent rester tels quels) |

---

## Plan de correction recommandé

1. **Correctifs rapides (bugs actuels)** : n°1, 2, 3, 6, 7.
2. **Passation fiable** : n°4 (`replace: true` + `sessionStorage`), n°5, n°10.
3. **Préparation du branchement backend** : n°12 (aligner les mocks), n°13 (couche `src/api/`), n°8 (DTO sans réponses).
4. **Refactoring** : n°14, n°15.
5. **Nettoyage** : n°16.
6. **À traiter avec l'équipe backend** : n°8, 9, 11.
