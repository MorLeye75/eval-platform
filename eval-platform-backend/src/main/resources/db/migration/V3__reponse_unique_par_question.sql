-- Une seule réponse par question et par copie. Sans cette contrainte, une sauvegarde contenant plusieurs fois
-- le même questionId créait autant de réponses, toutes notées puis additionnées dans la note finale.

-- 1. Doublons déjà présents : on garde la réponse la plus ancienne (celle que les sauvegardes suivantes
--    mettaient à jour) et on supprime les autres avec leurs lignes dépendantes (pas de ON DELETE CASCADE).
create temporary table reponses_en_double on commit drop as
select r.id
from reponses_etudiant r
where exists (
    select 1 from reponses_etudiant autre
    where autre.soumission_id = r.soumission_id
      and autre.question_id = r.question_id
      and autre.id < r.id
);

create temporary table propositions_en_double on commit drop as
select o.proposition_ia_id as id
from reponses_ouvertes o
where o.id in (select id from reponses_en_double)
  and o.proposition_ia_id is not null;

delete from reponse_qcm_choix where reponse_qcm_id in (select id from reponses_en_double);
delete from resultats_cas_tests where reponse_code_id in (select id from reponses_en_double);
delete from reponses_qcm where id in (select id from reponses_en_double);
delete from reponses_code where id in (select id from reponses_en_double);
delete from reponses_ouvertes where id in (select id from reponses_en_double);
delete from propositions_ia where id in (select id from propositions_en_double);
delete from reponses_etudiant where id in (select id from reponses_en_double);

-- 2. Contrainte : l'index unique sert aussi aux recherches par copie (soumission_id en tête)
alter table reponses_etudiant
    add constraint uk_reponses_soumission_question unique (soumission_id, question_id);
