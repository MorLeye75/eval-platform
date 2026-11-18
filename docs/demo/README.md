# Démonstration de la plateforme

Deux façons de la dérouler : le **script** (vérifie tout par l'API, 31 étapes) et le **parcours à l'écran** (ce qu'on montre).

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

Le script crée ses propres données (suffixe unique) : il peut être relancé sans redémarrer le backend. Il se termine par `31/31 étapes réussies`.

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

## À dire franchement si on pose la question

- La **correction IA** des questions ouvertes utilise un correcteur de démonstration : aucun vrai modèle n'est branché (fournisseur, clé et coût à décider avec l'encadrant). L'enseignant valide ou note à la main chaque réponse.
- L'**exécution du code** passe par Judge0 : un test réel contre une instance Judge0 reste à faire ; les tests automatiques utilisent un faux exécuteur.
- La page **Paramètres système** de l'administrateur est un aperçu : rien n'y est enregistré côté serveur, et l'écran le dit.
- Une évaluation créée pendant la démonstration est datée de 2030 : elle ne s'ouvre pas tout de suite. Pour montrer la passation, utiliser les évaluations de démonstration déjà ouvertes.
