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
| Étudiant | `etudiant@ept.sn` (Aminou Niang, DIC2) | `etudiant123` |
| Deuxième enseignant | `prof2@ept.sn` (Awa Diagne, DIC3) | `prof123` |
| Autres étudiants | `prenom.nom@ept.sn`, sans accent : `fatou.sarr@ept.sn`, `aissatou.diallo@ept.sn`… | `etudiant123` |

La base H2 repart de zéro à chaque démarrage : relancer le backend remet la démonstration à l'état initial.

## Données de démonstration

Créées au démarrage en profil `h2-dev` ou `dev` (`app.demo.jeu-complet`), jamais en production. Les notes suivent les
règles de la correction (QCM avec barème négatif, code au prorata des cas de test, questions ouvertes validées).

| Évaluation | État | Ce qu'elle permet de montrer |
| :--- | :--- | :--- |
| Structures de données — QCM, Algorithmique — Exercice de code, Bases de données — Quiz chronométré | ouvertes | la passation (QCM, code dans Monaco, quiz chronométré), la supervision en direct |
| Réseaux — QCM de mi-semestre | terminée il y a 3 semaines, **publiée** | barème négatif ; un absent ; résultats et performances de l'étudiant |
| Bases de données — Partiel | terminée il y a une semaine, **publiée** | statistiques sur 12 copies, code avec verdicts, **note revue avant publication** (copie de Moussa Diop : historique), alerte d'onglet |
| **Programmation — Contrôle continu** | terminée il y a 1 h, **à corriger et publier** | copie d'Aminou en attente (code en repli manuel à noter, proposition de l'IA à valider), tiers-temps, absent, alerte ; publiable tout de suite |
| Systèmes distribués — Examen | publiée, à `prof2@ept.sn` (DIC3) | un autre enseignant : `prof@ept.sn` n'y a pas accès (403) ; statistiques globales de l'admin sur deux promotions |
| Génie logiciel — Patrons de conception | brouillon | modifier, prévisualiser, publier une évaluation |
| Systèmes d'exploitation — Ordonnancement | publiée, ouvre dans 2 jours | une évaluation à venir côté étudiant |

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
5. **Enseignant** : *Supervision* en direct (qui a commencé, qui a remis, alertes comme indices), puis la correction et la publication du **contrôle continu** :
   - *Statistiques* → « Programmation — Contrôle continu » → *Notes par étudiant* → **Voir la copie** d'Aminou : code dans Monaco, noter le code (Judge0 indisponible), revoir la note du QCM avec un motif.
   - *Validation IA* : valider les propositions en attente (Aminou, Fatou Sarr). Les copies passent notées.
   - *Publication résultats* → le contrôle continu est « à publier » → **Publier**. Connecté en Aminou : notification, note, détail, motif de la révision.
   - Revenir sur la copie : « Les résultats sont publiés : les notes ne sont plus modifiables ». Montrer aussi l'historique sur la copie de Moussa Diop (Partiel).
6. **Administrateur** : *Tableau de bord*, *Supervision globale*, *Statistiques globales* (notes ramenées sur 20 ; « — » quand il n'y a pas de données).
7. **Sécurité** : réinitialiser le mot de passe d'un compte connecté : sa session est coupée immédiatement.

## À dire franchement si on pose la question

- La **correction IA** des questions ouvertes utilise un correcteur de démonstration : aucun vrai modèle n'est branché (fournisseur, clé et coût à décider avec l'encadrant). L'enseignant valide ou note à la main chaque réponse.
- L'**exécution du code** passe par Judge0 : un test réel contre une instance Judge0 reste à faire ; les tests automatiques utilisent un faux exécuteur.
- La page **Paramètres système** de l'administrateur est en **lecture seule** : elle montre ce que le serveur applique réellement (valeurs du code ou du déploiement), mais rien ne s'y modifie.
- Une évaluation créée pendant la démonstration est datée de 2030 : elle ne s'ouvre pas tout de suite. Pour montrer la passation, utiliser les évaluations de démonstration déjà ouvertes.
