-- Notifications (DS-15) : lien vers l'évaluation et vers la copie, pour que le front ouvre directement la bonne
-- page (/etudiant/resultats/{soumissionId}). Nuls pour une notification sans objet. Supprimées avec leur objet.
alter table notifications add column evaluation_id bigint;
alter table notifications add column soumission_id bigint;

alter table notifications add constraint fk_notifications_evaluation
    foreign key (evaluation_id) references evaluations (id) on delete cascade;
alter table notifications add constraint fk_notifications_soumission
    foreign key (soumission_id) references soumissions (id) on delete cascade;

-- Compteur de la cloche (interrogé toutes les 60 s) et liste paginée, par destinataire.
-- Ils remplacent l'index simple sur utilisateur_id, qu'ils couvrent.
drop index if exists idx_notifications_destinataire;
create index idx_notifications_non_lues on notifications (utilisateur_id, lue);
create index idx_notifications_liste on notifications (utilisateur_id, date_envoi);
-- Pas de doublon « résultat publié » pour une même copie, et suppression en cascade sans balayer la table
create index idx_notifications_soumission on notifications (soumission_id);
-- Purge des notifications lues anciennes
create index idx_notifications_purge on notifications (lue, date_envoi);
