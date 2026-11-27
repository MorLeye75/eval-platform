# Démonstration de la plateforme

Deux façons de la dérouler : le **script** (vérifie tout par l'API, 32 étapes) et le **parcours à l'écran** (ce qu'on montre).

## Préparer

```bash
# 1. Backend, base en mémoire avec les comptes de démonstration
cd eval-platform-backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2-dev

# 2. Frontend
cd eval-platform-frontend
npm install && npm run dev      # http://localhost:5173
```

| Rôle | E-mail | Mot de passe |
| :--- | :--- | :--- |
| Administrateur | `admin@ept.sn` | `admin123` |
| Enseignant | `prof@ept.sn` | `prof123` |
| Étudiant | `etudiant@ept.sn` | `etudiant123` |

La base H2 repart de zéro à chaque démarrage : relancer le backend remet la démonstration à l'état initial.

## Vérifier avant de présenter

```bash
node docs/demo/parcours-demo.mjs        # attend le backend sur http://localhost:8080/api
```

Le script crée ses propres données (suffixe unique) : il peut être relancé sans redémarrer le backend. Il se termine par `31/32 étapes réussies`.

## Parcours à l'écran (environ 10 minutes)

1. **Administrateur** (`admin@ept.sn`)
   - *Gestion des comptes* : créer une promotion, la renommer ; créer un étudiant. Le mot de passe provisoire s'affiche **une seule fois**.
   - Essayer de supprimer une promotion qui a des étudiants : refus avec le motif.
2. **Premier accès de l'étudiant créé** : connexion avec le mot de passe provisoire. Il est renvoyé vers son profil et ne peut rien faire d'autre avant de le changer (le serveur refuse le reste, 403).
3. **Enseignant** (`prof@ept.sn`)
   - *Créer une évaluation* (assistant en trois étapes : informations, questions, aperçu) ; la **dupliquer** depuis la liste ; accorder un **tiers-temps** (un tiers-temps hors plage est refusé).
   - Publier l'évaluation. Publier ses résultats avant la fin est refusé.
4. **Étudiant** (`etudiant@ept.sn`) : ouvrir « Structures de données — QCM », répondre, changer d'onglet (alerte), remettre la copie. Le sujet ne contient jamais les bonnes réponses.
5. **Enseignant** : *Supervision* en direct (qui a commencé, qui a remis, alertes comme indices), *Statistiques*, *Notes par étudiant*, export CSV, publication des résultats.
6. **Administrateur** : *Tableau de bord*, *Supervision globale*, *Statistiques globales* (notes ramenées sur 20 ; « — » quand il n'y a pas de données).
7. **Sécurité** : réinitialiser le mot de passe d'un compte connecté : sa session est coupée immédiatement.

## Correction IA avec un vrai modèle (Groq, gratuit)

Par défaut, la correction des questions ouvertes utilise un correcteur **simulé** : la démonstration fonctionne sans réseau, mais il ne comprend pas le texte. Pour montrer un vrai modèle, il faut le brancher **avant** de lancer le backend.

> **Confidentialité.** Avec un modèle réel, le texte des réponses part chez le fournisseur. Pour la démonstration, n'utiliser que des réponses **fictives** (le script ci-dessous n'en crée que de fictives). Pour de vraies copies, c'est une décision à prendre avec l'établissement.

1. **Créer une clé** sur `console.groq.com` (compte gratuit). La copier une seule fois : elle ne se met ni dans le dépôt, ni dans un message.
2. **La donner au backend**, sans l'écrire dans un fichier versionné : soit variables d'environnement, soit le fichier `eval-platform-backend/.env` (ignoré par git) :
   ```
   CORRECTION_IA_FOURNISSEUR=llm
   LLM_API_KEY=<votre clé>
   ```
   Le modèle par défaut est `openai/gpt-oss-120b` ; `LLM_MODEL`, `LLM_URL` et `LLM_EFFORT_RAISONNEMENT` se changent au besoin (voir le README du backend). Sans clé, le backend **refuse de démarrer** avec `llm` : c'est voulu.
3. **Relancer le backend.** La page « Paramètres système » de l'administrateur affiche alors « modèle externe … : le texte des réponses est envoyé au fournisseur ».
4. **Préparer les données de démonstration.** Les évaluations de démonstration n'ont pas de question ouverte, donc rien à corriger. Ce script crée une évaluation à questions ouvertes, la fait passer par trois étudiants fictifs (bonne réponse, réponse partielle, tentative de manipulation) et attend qu'elle soit terminée (**environ 6 minutes**) :
   ```bash
   node docs/demo/preparer-correction-ia.mjs --generer
   ```
   À lancer **la veille** (ou au moins 15 minutes avant) : avec `--generer`, les propositions sont déjà présentes à l'écran et la démonstration ne dépend plus du réseau. Sans `--generer`, on clique « Générer les propositions IA » devant le public.
5. **À montrer** : dans l'espace enseignant, « Validation IA ». Valider la bonne réponse, ajuster la réponse partielle, et regarder ce que le modèle propose pour la **tentative de manipulation** : son texte est traité comme une donnée, jamais comme une consigne, et de toute façon c'est l'enseignant qui décide (une proposition n'écrit aucune note). La réponse vide reçoit 0 sans même appeler le modèle. **Vérifier cette page la veille** : le comportement d'un modèle varie, ne rien promettre à l'avance.

**Si le modèle est indisponible** (réseau, quota, clé refusée), aucune proposition n'est créée : la réponse apparaît dans « À noter à la main ». La démonstration continue sans le modèle.

## À dire franchement si on pose la question

- La **correction IA** des questions ouvertes utilise par défaut un correcteur de démonstration. Un vrai modèle (Groq) peut être branché (section ci-dessus), mais il envoie le texte des réponses au fournisseur : réponses fictives en démonstration, décision de confidentialité à prendre avec l'établissement pour de vraies copies. L'enseignant valide ou note à la main chaque réponse.
- L'**exécution du code** passe par Judge0 : un test réel contre une instance Judge0 reste à faire ; les tests automatiques utilisent un faux exécuteur.
- La page **Paramètres système** de l'administrateur est en **lecture seule** : elle montre ce que le serveur applique réellement (valeurs du code ou du déploiement), mais rien ne s'y modifie.
- Une évaluation créée pendant la démonstration est datée de 2030 : elle ne s'ouvre pas tout de suite. Pour montrer la passation, utiliser les évaluations de démonstration déjà ouvertes.
