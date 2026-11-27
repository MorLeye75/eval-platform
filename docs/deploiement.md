# Déployer la plateforme d'évaluation

Procédure pour installer la plateforme sur un serveur Linux de l'établissement, sans Docker : un backend Spring Boot
(fichier `.jar`), un frontend statique servi par nginx, une base PostgreSQL et Judge0 sur un hôte interne. Chaque
étape se termine par une vérification.

## 1. Architecture

```
Navigateur ──HTTPS──▶ nginx (eval.ept.sn)
                        ├── /        → frontend statique (dist/)
                        └── /api/    → backend Spring Boot (127.0.0.1:8080, service systemd)
                                          ├── PostgreSQL (127.0.0.1:5432)
                                          ├── Judge0 (hôte interne, cgroups v1, jamais exposé)
                                          ├── SMTP (mot de passe oublié)
                                          └── Groq (facultatif, correction IA : texte des réponses envoyé)
```

Seul nginx est joignable depuis l'extérieur (ports 80 et 443). Le backend, PostgreSQL et Judge0 n'écoutent que sur des
adresses internes.

## 2. Prérequis

| Élément | Version | Rôle |
|---|---|---|
| Serveur | Ubuntu 22.04 ou 24.04, 2 vCPU, 4 Go de RAM, 20 Go de disque | backend, base, frontend |
| Java | 21 (Temurin ou OpenJDK) | exécuter le backend |
| PostgreSQL | 16 ou 17 | données (migrations Flyway V1 à V18 appliquées au démarrage) |
| nginx et certbot | paquets Ubuntu | HTTPS, frontend, proxy de l'API |
| Node.js | 22 | **construction** du frontend seulement (sur le serveur ou un poste) |
| Hôte Judge0 | Ubuntu 20.04, ou 22.04 passé en cgroups v1 | correction du code (voir `deploy/judge0/README.md`) |
| Serveur SMTP | celui de l'établissement | e-mails de réinitialisation du mot de passe |
| Nom de domaine | ex. `eval.ept.sn`, pointant vers le serveur | HTTPS |

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless postgresql nginx certbot python3-certbot-nginx
```

## 3. Base de données

```bash
sudo -u postgres psql <<'SQL'
CREATE USER eval WITH PASSWORD 'mot-de-passe-long-et-aleatoire';
CREATE DATABASE eval_platform_db OWNER eval;
SQL
```

Générer le mot de passe avec `openssl rand -base64 32`. PostgreSQL n'écoute que sur `localhost` par défaut : le
laisser ainsi.

**Vérifier** : `psql -h 127.0.0.1 -U eval -d eval_platform_db -c 'select 1'` répond `1`.

## 4. Construire les livrables

Construire depuis la branche principale, **une fois l'intégration continue verte**.

```bash
git clone https://github.com/MorLeye75/eval-platform.git && cd eval-platform

# Backend : un seul fichier exécutable
cd eval-platform-backend
mvn clean package -DskipTests         # les tests ont tourné dans l'intégration continue
# -> target/backend-0.0.1-SNAPSHOT.jar

# Frontend : l'API est servie sur le même domaine, sous /api
cd ../eval-platform-frontend
npm ci
VITE_API_URL=/api npm run build
# -> dist/
```

`VITE_API_URL` est lu **au moment de la construction** : le changer demande de reconstruire le frontend.

## 5. Installer le backend

```bash
sudo useradd --system --home /opt/eval-platform --shell /usr/sbin/nologin eval
sudo mkdir -p /opt/eval-platform /etc/eval-platform
sudo cp eval-platform-backend/target/backend-0.0.1-SNAPSHOT.jar /opt/eval-platform/backend.jar
sudo chown -R eval:eval /opt/eval-platform
```

### Configuration : `/etc/eval-platform/backend.env`

Le seul endroit où vivent les secrets. Jamais dans le dépôt ni dans un message.

```ini
SPRING_PROFILES_ACTIVE=prod

# Base
DB_URL=jdbc:postgresql://127.0.0.1:5432/eval_platform_db
DB_USERNAME=eval
DB_PASSWORD=<mot de passe de l'étape 3>

# Jetons de connexion : 256 bits minimum, en Base64 (openssl rand -base64 64)
JWT_SECRET=<secret>

# Adresse publique du frontend
CORS_ALLOWED_ORIGINS=https://eval.ept.sn
FRONTEND_URL=https://eval.ept.sn
# Derrière nginx : lire la vraie adresse du client (limite des tentatives de connexion)
FORWARD_HEADERS_STRATEGY=native

# E-mails (mot de passe oublié). Sans eux, les liens sont écrits dans le journal : inacceptable en production
SPRING_MAIL_HOST=smtp.ept.sn
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<compte>
SPRING_MAIL_PASSWORD=<mot de passe>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_EXPEDITEUR=no-reply@ept.sn

# Judge0 sur le réseau interne (voir deploy/judge0)
JUDGE0_URL=http://10.0.0.20:2358
JUDGE0_TOKEN=<AUTHN_TOKEN de judge0.conf>
JUDGE0_CONCURRENCE=2

# Correction IA : simule par défaut. llm envoie le texte des réponses au fournisseur (accord de l'établissement requis)
CORRECTION_IA_FOURNISSEUR=simule
# LLM_API_KEY=<clé Groq>

# Premier administrateur : créé au premier démarrage s'il n'en existe aucun, mot de passe provisoire (12 caractères
# minimum) à changer à la première connexion. RETIRER ces deux lignes une fois connecté.
ADMIN_INITIAL_EMAIL=direction@ept.sn
ADMIN_INITIAL_MOT_DE_PASSE=<mot de passe provisoire>
```

```bash
sudo chown root:eval /etc/eval-platform/backend.env
sudo chmod 640 /etc/eval-platform/backend.env
```

Toutes les variables sont décrites dans `eval-platform-backend/README.md`. Sans `JWT_SECRET` ni `DB_PASSWORD`, ou
avec un secret trop court, le backend **refuse de démarrer** : c'est voulu. En production, aucun compte de
démonstration n'est créé.

### Service : `/etc/systemd/system/eval-platform.service`

```ini
[Unit]
Description=Plateforme d'évaluation EPT (backend)
After=network.target postgresql.service

[Service]
User=eval
Group=eval
WorkingDirectory=/opt/eval-platform
EnvironmentFile=/etc/eval-platform/backend.env
ExecStart=/usr/bin/java -Xms256m -Xmx1g -jar /opt/eval-platform/backend.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
NoNewPrivileges=true
ProtectSystem=full
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now eval-platform
journalctl -u eval-platform -f        # suivre le démarrage
```

**Vérifier** :
- le journal affiche les migrations Flyway, puis `Premier administrateur créé : direction@ept.sn`, puis
  `Started EvalBackendApplication` ;
- `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/auth/me` répond `401` (serveur prêt, connexion
  exigée).

## 6. Installer le frontend et nginx

```bash
sudo mkdir -p /var/www/eval-platform
sudo cp -r eval-platform-frontend/dist/* /var/www/eval-platform/
```

`/etc/nginx/sites-available/eval-platform` :

```nginx
server {
    listen 80;
    server_name eval.ept.sn;

    root /var/www/eval-platform;
    index index.html;

    # Application d'une seule page : toute adresse inconnue renvoie index.html (le routeur React prend le relais)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Fichiers construits (noms contenant une empreinte) : cache long ; index.html jamais en cache
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    location = /index.html {
        add_header Cache-Control "no-cache";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        client_max_body_size 5m;          # import CSV des étudiants
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/eval-platform /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d eval.ept.sn     # HTTPS et redirection automatique de http vers https
```

**Vérifier** : `https://eval.ept.sn` affiche la page de connexion, et `https://eval.ept.sn/api/auth/me` répond `401`.

## 7. Judge0

Suivre `deploy/judge0/README.md` sur l'hôte interne (cgroups v1 obligatoire), puis lancer `verifier.sh`. Le port 2358
ne doit être ouvert **qu'au serveur de la plateforme** (pare-feu). Sans Judge0, la plateforme fonctionne : les
questions de code passent en correction manuelle après quelques nouvelles tentatives.

## 8. Premier démarrage

1. Se connecter sur `https://eval.ept.sn` avec `ADMIN_INITIAL_EMAIL` et `ADMIN_INITIAL_MOT_DE_PASSE`. La plateforme
   impose de changer le mot de passe.
2. **Retirer** `ADMIN_INITIAL_EMAIL` et `ADMIN_INITIAL_MOT_DE_PASSE` de `backend.env`, puis
   `sudo systemctl restart eval-platform`.
3. Dans « Gestion des comptes » : créer les promotions, importer les étudiants (CSV), créer les enseignants. Chaque
   compte reçoit un mot de passe provisoire, affiché une seule fois, à changer à la première connexion.
4. Vérifier la page « Paramètres système » : elle affiche la configuration réellement appliquée (sans secret).
5. Envoyer un lien « mot de passe oublié » à une adresse de test : l'e-mail doit arriver.

## 9. Sauvegardes

Les copies et les notes sont dans PostgreSQL : une sauvegarde quotidienne, gardée hors du serveur.

`/etc/cron.d/eval-platform-sauvegarde` :

```cron
30 2 * * * postgres pg_dump -Fc eval_platform_db > /var/backups/eval-platform/eval-$(date +\%F).dump && find /var/backups/eval-platform -name '*.dump' -mtime +30 -delete
```

Copier `/var/backups/eval-platform` vers un autre serveur (rsync). **Tester la restauration** au moins une fois :

```bash
createdb -U postgres eval_restauration
pg_restore -U postgres -d eval_restauration /var/backups/eval-platform/eval-AAAA-MM-JJ.dump
```

## 10. Mettre à jour

À faire **hors période d'examen** (le redémarrage coupe les connexions une dizaine de secondes ; les copies en cours
sont sauvegardées et reprennent).

```bash
# 1. Sauvegarde avant toute mise à jour
sudo -u postgres pg_dump -Fc eval_platform_db > /var/backups/eval-platform/avant-maj-$(date +%F).dump

# 2. Nouveaux livrables (étape 4), puis :
sudo cp backend-0.0.1-SNAPSHOT.jar /opt/eval-platform/backend.jar.nouveau
sudo mv /opt/eval-platform/backend.jar /opt/eval-platform/backend.jar.precedent
sudo mv /opt/eval-platform/backend.jar.nouveau /opt/eval-platform/backend.jar
sudo systemctl restart eval-platform        # Flyway applique les nouvelles migrations
sudo rsync -a --delete dist/ /var/www/eval-platform/

# 3. Vérifier (étapes 5 et 6), et se connecter
```

**Revenir en arrière** si le démarrage échoue : remettre `backend.jar.precedent` et le frontend précédent. Si une
migration a été appliquée, restaurer aussi la sauvegarde faite à l'étape 1 (une migration ne s'annule pas seule).

## 11. Exploitation

| Besoin | Commande |
|---|---|
| État du service | `systemctl status eval-platform` |
| Journal en direct | `journalctl -u eval-platform -f` |
| Erreurs des dernières 24 h | `journalctl -u eval-platform --since "24 hours ago" -p warning` |
| Redémarrer | `sudo systemctl restart eval-platform` |
| Certificat HTTPS | renouvelé automatiquement par certbot (`systemctl list-timers \| grep certbot`) |

Avant un examen important : vérifier l'espace disque (`df -h`), la dernière sauvegarde, et faire un essai complet avec
un compte étudiant de test.

## 12. Liste de vérification

- [ ] CI verte sur la version déployée
- [ ] `backend.env` rempli, droits `640`, aucun secret ailleurs
- [ ] HTTPS actif, `http` redirigé vers `https`
- [ ] Seul nginx exposé (ports 80 et 443) ; PostgreSQL et Judge0 inaccessibles de l'extérieur
- [ ] Premier administrateur connecté, mot de passe changé, variables `ADMIN_INITIAL_*` retirées
- [ ] E-mail de réinitialisation reçu
- [ ] `verifier.sh` de Judge0 réussi depuis le serveur de la plateforme
- [ ] Sauvegarde quotidienne en place, restauration testée
- [ ] Correction IA : `simule`, ou `llm` avec l'accord écrit de l'établissement
