# Stratégie de déploiement

Comment la plateforme serait mise en production à l'EPT. Ce document fixe les choix et les grandes étapes ; il ne
décrit pas un déploiement réalisé.

## Architecture cible

```
Navigateur ──HTTPS──▶ nginx (ex. eval.ept.sn)
                        ├── /        → frontend statique (build Vite)
                        └── /api/    → backend Spring Boot (.jar, service système, 127.0.0.1:8080)
                                          ├── PostgreSQL (même serveur, accès local uniquement)
                                          ├── Judge0 (hôte interne dédié, jamais exposé)
                                          ├── SMTP de l'établissement (mot de passe oublié)
                                          └── Groq (facultatif : correction IA, le texte des réponses sort)
```

Un seul serveur Linux suffit pour une école (2 vCPU, 4 Go de RAM), plus un hôte pour Judge0. Seul nginx est joignable
de l'extérieur.

## Choix

| Choix | Raison |
|---|---|
| **Sans Docker pour l'application** | Un .jar, des fichiers statiques et PostgreSQL : peu de pièces, faciles à vérifier et à maintenir par l'équipe de l'école. Seul Judge0, qui l'impose, tourne avec son docker-compose (`deploy/judge0`). |
| **Serveur Linux de l'établissement** (Ubuntu) | Les copies et les notes restent dans l'école (souveraineté) ; pas d'abonnement. |
| **nginx en frontal, HTTPS** (Let's Encrypt) | Un seul domaine pour le frontend et l'API : pas de CORS ouvert, cache des fichiers statiques, certificat renouvelé automatiquement. |
| **Backend en service système** (systemd) | Redémarrage automatique, journal centralisé, démarrage avec le serveur. |
| **PostgreSQL local, migrations Flyway** | Le schéma se met à jour seul au démarrage (V1 à V18) ; Hibernate vérifie qu'il correspond au code. |
| **Judge0 sur un hôte séparé** | Le code des étudiants ne s'exécute jamais sur le serveur de la plateforme ; Judge0 exige les cgroups v1 (Ubuntu 20.04, ou plus récent réglé). |
| **Correction IA simulée par défaut** | Le vrai modèle (Groq) envoie le texte des réponses à un tiers : à activer seulement avec l'accord de l'établissement. |

## Grandes étapes

1. **Construire** depuis la branche principale, CI verte : le `.jar` du backend et le build statique du frontend.
2. **Préparer le serveur** : Java 21, PostgreSQL, nginx ; une base et un utilisateur dédiés.
3. **Configurer** par variables d'environnement, dans un seul fichier protégé : secrets de la base et des jetons,
   adresse publique, SMTP, Judge0, IA. Sans secret, ou avec un secret trop court, le backend refuse de démarrer.
4. **Démarrer** le backend (les migrations s'appliquent), servir le frontend par nginx, activer HTTPS.
5. **Installer Judge0** sur l'hôte interne et vérifier une exécution (`deploy/judge0/verifier.sh`).
6. **Créer le premier administrateur** : en production, aucun compte de démonstration n'existe, et seul un
   administrateur peut créer des comptes. **Prérequis à traiter avant un vrai déploiement** : un moyen de créer ce
   premier compte (par exemple au premier démarrage, depuis deux variables d'environnement, avec un mot de passe
   provisoire à changer à la première connexion). L'administrateur crée ensuite les promotions, importe les
   étudiants (CSV) et crée les enseignants.
7. **Vérifier** : page de connexion en HTTPS, e-mail de réinitialisation reçu, une épreuve d'essai de bout en bout.

Toutes les variables sont décrites dans `eval-platform-backend/README.md`.

## Sécurité

- Secrets uniquement dans l'environnement du serveur, jamais dans le dépôt ; le fichier `.env` local est ignoré par git.
- Seuls les ports 80 et 443 sont ouverts ; PostgreSQL et Judge0 n'acceptent que le serveur de la plateforme.
- `FORWARD_HEADERS_STRATEGY=native` derrière nginx, pour que la limite des tentatives de connexion voie la vraie
  adresse des clients.
- Swagger et les comptes de démonstration sont désactivés hors développement.

## Exploitation

- **Sauvegardes** : `pg_dump` quotidien de la base (copies, notes, historique), gardé hors du serveur ; restauration
  testée.
- **Mises à jour** hors période d'examen : sauvegarde, nouveau `.jar` et nouveau frontend, redémarrage (Flyway applique
  les nouvelles migrations). Retour arrière : version précédente et, si une migration est passée, la sauvegarde.
- **Surveillance** : journal du service (`journalctl`), espace disque, renouvellement du certificat ; essai complet
  avant chaque examen important.

## Risques et réponses

| Risque | Réponse |
|---|---|
| Panne du serveur pendant un examen | Les copies sont sauvegardées en continu (navigateur et serveur) et reprennent au redémarrage ; la clôture automatique traite ensuite les copies abandonnées. |
| Judge0 indisponible | La remise n'en dépend pas : nouvelles tentatives, puis correction manuelle ; l'enseignant note le code depuis la copie. |
| Fournisseur IA indisponible ou refusé | Correcteur simulé ou notes à la main : aucune note n'est jamais écrite sans l'enseignant. |
| Plusieurs instances du backend | Non prévu : les compteurs de tentatives de connexion sont en mémoire ; une seule instance suffit pour une école. |
| Perte de données | Sauvegarde quotidienne hors serveur, et avant chaque mise à jour. |

## Hors périmètre

Conteneurisation complète de l'application, haute disponibilité (plusieurs serveurs), hébergement permanent de Judge0 :
à décider avec l'établissement selon ses moyens.
