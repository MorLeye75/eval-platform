# Judge0 auto-hébergé (correction automatique du code, DS-12)

Judge0 CE 1.13.1 exécute le code des étudiants dans un bac à sable (isolate) et compare la sortie standard à la
sortie attendue de chaque cas de test. Le backend l'appelle sur le réseau interne ; il n'est jamais exposé
publiquement.

## Prérequis : un hôte Linux en cgroups v1

Le bac à sable de Judge0 1.13.1 exige les **cgroups v1**. Sur un hôte en cgroups v2, Judge0 démarre et répond,
mais chaque exécution échoue avec le statut 13 « Internal Error ». La plateforme compte alors l'échec comme
« Judge0 indisponible » : nouvel essai 1, 2, 4 puis 8 min plus tard, puis correction manuelle.

| Hôte | Utilisable |
|---|---|
| Ubuntu 20.04 (cgroups v1 par défaut) | oui |
| Ubuntu 22.04 ou plus récent | oui, après passage en cgroups v1 : ajouter `systemd.unified_cgroup_hierarchy=0` à `GRUB_CMDLINE_LINUX` dans `/etc/default/grub`, puis `sudo update-grub` et redémarrer |
| Docker Desktop sous Windows (WSL 2.5.1 et plus) | **non** : WSL ne propose plus les cgroups v1 |

Vérifier la version des cgroups d'un hôte : `docker info --format '{{.CgroupVersion}}'` doit afficher `1`.

## Démarrage

```bash
cd deploy/judge0
cp judge0.conf.example judge0.conf
# renseigner AUTHN_TOKEN, REDIS_PASSWORD et POSTGRES_PASSWORD (chaînes aléatoires, ex. openssl rand -hex 24)
docker compose up -d
JUDGE0_TOKEN=<valeur de AUTHN_TOKEN> ./verifier.sh
```

`verifier.sh` contrôle la présence des cinq langages de la plateforme, puis exécute un petit programme Python :
il doit afficher « statut 3 « Accepted » ». Le premier démarrage télécharge l'image de Judge0 (plusieurs Go).

## Vérifier la correction de bout en bout

`verifier.sh` teste Judge0 seul. `Judge0ReelIntegrationTest` (backend) teste toute la chaîne contre ce Judge0 : remise
d'une copie de code, exécution des cas de test, verdicts et note, sur six scénarios (Python juste et faux, Java,
erreur de compilation en C, erreur à l'exécution, boucle infinie). Il est ignoré tant que `JUDGE0_REEL_URL` n'est pas
défini, donc en CI.

```bash
cd eval-platform-backend
JUDGE0_REEL_URL=http://localhost:2358 JUDGE0_REEL_TOKEN=<valeur de AUTHN_TOKEN> \
  mvn test -Dtest=Judge0ReelIntegrationTest
```

Sur un hôte lent (machine virtuelle sans accélération matérielle), allonger les délais :
`JUDGE0_REEL_DELAI_LECTURE=60s JUDGE0_REEL_DUREE_MAX=30m JUDGE0_REEL_ATTENTE_MINUTES=30`.

**Résultat du 14 novembre 2026** : `verifier.sh` et les six scénarios passent contre Judge0 1.13.1, dans une VM
VirtualBox Ubuntu 20.04 (cgroups v1) créée avec Vagrant sur le poste de développement. Sur ce poste, WSL 2 occupe
l'hyperviseur : VirtualBox passe par lui et la VM est très lente (une compilation Java a pris jusqu'à 17 minutes au
premier essai), d'où les délais allongés. Sur un vrai serveur Linux, les délais par défaut suffisent.

## Brancher le backend

| Variable du backend | Valeur |
|---|---|
| `JUDGE0_URL` | `http://localhost:2358` (même machine) ou l'adresse interne de l'hôte Judge0 |
| `JUDGE0_TOKEN` | la valeur de `AUTHN_TOKEN` de `judge0.conf` |
| `JUDGE0_CONCURRENCE` | la valeur de `COUNT` de `judge0.conf` (nombre de workers, 2 par défaut) |

Le port 2358 n'est publié que sur `127.0.0.1`. Si le backend tourne sur une autre machine, publier le port sur
l'adresse du réseau interne seulement, et le fermer au pare-feu pour le reste du monde.

## Choix de configuration (`judge0.conf.example`)

- `ALLOW_ENABLE_NETWORK=false` : un programme d'étudiant n'accède jamais au réseau, même si une requête le demande.
- `ENABLE_CALLBACKS=false` : Judge0 n'appelle aucune URL extérieure ; le backend interroge les résultats.
- `ENABLE_COMPILER_OPTIONS=false`, `ENABLE_COMMAND_LINE_ARGUMENTS=false` : fonctions non utilisées, désactivées.
- `ENABLE_WAIT_RESULT=false` : pas d'attente synchrone qui occuperait le serveur.
- Les limites d'exécution (temps, mémoire, processus, taille de fichier) sont envoyées par le backend avec chaque
  soumission (`correction.judge0.*`) et restent sous les maximums par défaut de Judge0.
