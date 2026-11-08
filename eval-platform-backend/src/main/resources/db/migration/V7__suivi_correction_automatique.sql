-- Suivi de la correction automatique (DS-12) :
--  - correction_demarree_le : prise en charge d'une correction en cours (jeton revérifié avant d'écrire) ;
--  - tentatives_correction : échecs successifs (Judge0 indisponible, erreur), repli en correction manuelle à 5 ;
--  - prochaine_tentative_le : date avant laquelle la relance planifiée ne reprend pas la copie.
alter table soumissions add column correction_demarree_le timestamp(6);
alter table soumissions add column tentatives_correction integer not null default 0;
alter table soumissions add column prochaine_tentative_le timestamp(6);

-- La relance planifiée ne lit que les copies SOUMIS (une petite fraction de la table)
create index idx_soumissions_relance on soumissions (prochaine_tentative_le) where statut = 'SOUMIS';
