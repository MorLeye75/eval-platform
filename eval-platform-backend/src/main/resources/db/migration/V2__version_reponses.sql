-- Version des réponses enregistrées côté serveur (DS-02, DS-10) : compteur incrémenté à chaque sauvegarde
-- réussie ; le front ne restaure son tampon localStorage que s'il a été saisi sur la version actuelle
alter table soumissions add column version_reponses integer not null default 0;
