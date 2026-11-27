# Plan de présentation aux maîtres de stage

Démonstration d'environ **16 minutes**, suivie de questions. Elle complète `README.md` (préparation et parcours détaillé) ; ce document dit **quoi montrer, quoi dire et quoi faire si quelque chose se passe mal**.

## Message à faire passer

> Une plateforme d'évaluation **souveraine et auto-hébergée** : l'enseignant crée et publie, l'étudiant compose dans un environnement protégé, la correction est assistée mais **c'est toujours l'enseignant qui décide**, l'administrateur garde la main sur les comptes.

Trois idées à répéter :
1. **Équité et fiabilité** : tiers-temps, sauvegarde automatique, clôture automatique, notes publiées quand l'enseignant le décide.
2. **Intégrité** : le sujet ne contient jamais les bonnes réponses, les changements d'onglet sont signalés comme des *indices*, jamais des preuves.
3. **Maîtrise** : rien n'est inventé à l'écran ; sans donnée, on affiche « — », jamais un 0 trompeur.

## Déroulé (16 min)

| Min | Qui | Écran | À montrer | À dire |
| :-- | :-- | :-- | :-- | :-- |
| 0-1 | | Page de connexion | Les trois rôles | Une plateforme, trois espaces. |
| 1-4 | Admin | Gestion des comptes | Créer une promotion, un étudiant ; mot de passe provisoire affiché **une seule fois** | Pas d'inscription libre : l'administration crée les comptes (à justifier dans le rapport). |
| 4-5 | Étudiant créé | Premier accès | Il est renvoyé vers son profil et doit changer son mot de passe | Le serveur refuse tout le reste tant que ce n'est pas fait. |
| 5-8 | Enseignant | Créer une évaluation | Assistant en trois étapes, **duplication**, tiers-temps (un tiers-temps hors plage est refusé), publication | Les règles sont vérifiées par le serveur, l'écran ne fait que prévenir. |
| 8-10 | Étudiant | « Structures de données, QCM » | Répondre, changer d'onglet, remettre la copie | Sauvegarde automatique ; le sujet ne révèle aucune bonne réponse. |
| 10-12 | Enseignant | Supervision puis résultats | Supervision en direct, statistiques, notes par étudiant, **Comparer**, exports CSV / détail / PDF | Mêmes chiffres partout : l'écran, le CSV et le PDF sortent des mêmes données. |
| 12-14 | Enseignant puis étudiant | « Programmation — Contrôle continu » (terminée, à publier) | **Voir la copie** d'Aminou : code dans Monaco et verdicts, noter le code (Judge0 indisponible), **revoir une note avec motif** ; *Validation IA* ; **publier** ; Aminou voit note, détail et motif ; la copie n'est plus modifiable | L'enseignant relit et peut corriger toute note avant publication, motif obligatoire et historique ; après publication, plus rien ne change. |
| 14-16 | Admin | Tableau de bord, statistiques globales, paramètres | Notes ramenées sur 20 ; page Paramètres en lecture seule | La configuration affichée est celle que le serveur applique réellement. |

Pour la passation, utiliser les évaluations de démonstration **déjà ouvertes** : une évaluation créée pendant la démonstration est datée de 2030 et ne s'ouvre pas.

## À dire franchement

- **Correction IA** : deux modes. Par défaut, un correcteur **simulé** (il compare des mots, ce n'est pas une vraie correction). Avec un modèle réel branché (Groq, `CORRECTION_IA_FOURNISSEUR=llm`), **le texte des réponses part chez le fournisseur** : en démonstration, uniquement des réponses fictives ; en production, une décision de confidentialité à prendre avec l'établissement. Dans tous les cas, l'enseignant valide, ajuste ou rejette chaque proposition, et note à la main si le modèle est indisponible.
- **Exécution du code (Judge0)** : testée le 14/11 contre une vraie instance (VM Ubuntu 20.04, `deploy/judge0`) ; les tests automatiques utilisent un faux exécuteur. Judge0 ne tourne pas sur le poste de démonstration : **les copies de code montrées sont pré-remplies**, leurs verdicts sont ceux que Judge0 donnerait pour le code affiché (un test le vérifie). Le dire dès qu'on ouvre une copie de code.
- **Paramètres système** : lecture seule, volontairement. Les rendre modifiables demande de brancher chaque réglage un par un.
- **Choix assumés** : une note ne change plus après la publication (voulu) ; pas de refresh token ni de pénalité de temps (écartés, justifiés dans le rapport) ; pas de clôture anticipée d'une évaluation par l'enseignant.

## Plan B

| Problème | Réaction |
| :-- | :-- |
| Le backend ne démarre pas | Basculer sur le profil mémoire : `mvn spring-boot:run -Dspring-boot.run.profiles=h2-dev` (aucune base nécessaire). |
| Une page affiche une erreur | Relancer le script `node docs/demo/parcours-demo.mjs` : s'il passe 32/32, le serveur est sain et le défaut est visuel. |
| Écran figé ou lent | Recharger la page ; les copies en cours sont sauvegardées. |
| Question de code demandée | Ouvrir une copie de code du jeu de démonstration (Monaco, verdicts) ; dire que les verdicts sont pré-remplis et montrer comment Judge0 est déployé (`deploy/judge0`, testé en VM) et le diagramme DS-12. |
| Panne totale | Montrer les diagrammes de séquence (`docs/diagrammes-sequence`, DS-01 à DS-19) et le script de démonstration. |

## Veille de la démonstration (checklist)

- [ ] Branche mergée, `git pull`, **plus aucun merge** dans les 12 heures qui précèdent.
- [ ] Base remise à zéro : `psql … -c "drop schema public cascade" -c "create schema public"`, puis relancer le backend (les migrations V1 à V18 s'appliquent seules, et le jeu de démonstration complet est recréé au démarrage).
- [ ] `mvn clean package -DskipTests` si le code a changé (un ancien fichier de migration resté dans `target` fait échouer le démarrage).
- [ ] Backend en profil `dev`, frontend `npm run dev`, ouvrir **`http://localhost:5173`** (pas `127.0.0.1`).
- [ ] `node docs/demo/parcours-demo.mjs` : **32/32**, puis relancer le backend pour repartir d'une base propre.
- [ ] Répétition complète à l'écran, avec chronomètre.
- [ ] Le jour J : ordinateur branché, veille désactivée, notifications coupées, deux fenêtres de navigateur (une par rôle), connexion Internet non nécessaire.

## Questions probables

| Question | Réponse |
| :-- | :-- |
| Comment évitez-vous la triche ? | Sujet sans bonnes réponses, signalement des changements d'onglet comme indices, tiers-temps tracés, clôture automatique ; jamais de décision automatique contre un étudiant. |
| Que se passe-t-il si le réseau coupe pendant l'examen ? | Sauvegarde automatique à double tampon (navigateur + serveur) ; la reprise redonne la copie. |
| Et si Judge0 ou le QCM donne une note injuste ? | L'enseignant relit la copie complète et revoit la note avant publication : motif obligatoire, montré à l'étudiant, historique conservé (ancienne note, nouvelle, auteur, date). Après publication, plus rien ne change. |
| L'IA peut-elle noter à la place de l'enseignant ? | Non : elle propose, l'enseignant valide, ajuste ou rejette. Aucune note n'est écrite sans sa décision. |
| Les données sont-elles protégées ? | Rôles vérifiés par le serveur, jetons révocables, mots de passe hachés, aucun secret dans les écrans de configuration, CSV protégés contre l'injection de formule. |
| Comment le déployer ? | Backend Spring Boot et PostgreSQL ; le Docker de l'application complète reste à trancher. |
| Quelle qualité de test ? | Environ 420 tests backend (dont PostgreSQL via Testcontainers) et 380 côté frontend, une collection Postman de 81 requêtes, un script de démonstration de 32 étapes, une intégration continue. |
