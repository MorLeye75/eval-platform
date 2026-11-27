-- Tables de liaison sans clé primaire ni index (V1) : doublons possibles et recherches inverses en parcours complet.
-- Les collections JPA correspondantes sont désormais des Set (Evaluation.promotions, ReponseQCM.choixCoches).

-- 1. Doublons éventuels : une seule ligne par couple (ctid : identifiant physique de la ligne PostgreSQL)
delete from evaluation_promotions a
using evaluation_promotions b
where a.evaluation_id = b.evaluation_id
  and a.promotion_id = b.promotion_id
  and a.ctid > b.ctid;

delete from reponse_qcm_choix a
using reponse_qcm_choix b
where a.reponse_qcm_id = b.reponse_qcm_id
  and a.choix_id = b.choix_id
  and a.ctid > b.ctid;

-- 2. Clés primaires composites (elles indexent aussi la première colonne)
alter table evaluation_promotions
    add constraint pk_evaluation_promotions primary key (evaluation_id, promotion_id);

alter table reponse_qcm_choix
    add constraint pk_reponse_qcm_choix primary key (reponse_qcm_id, choix_id);

-- 3. Recherches inverses : évaluations d'une promotion (liste étudiant), réponses ayant coché un choix
create index idx_evaluation_promotions_promotion on evaluation_promotions (promotion_id);
create index idx_reponse_qcm_choix_choix on reponse_qcm_choix (choix_id);
