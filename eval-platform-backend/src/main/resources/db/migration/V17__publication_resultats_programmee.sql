-- DS-07 : publication différée des résultats. Seule la date est enregistrée ; la publication elle-même reste la bascule
-- atomique de resultats_publies (déclenchée par l'enseignant ou, à l'échéance, par le planificateur).
alter table evaluations add column publication_resultats_prevue_le timestamp;
