-- Jetons révoqués à la déconnexion (DS-14) : refusés jusqu'à leur expiration, même s'ils ont été copiés.
-- Une ligne par session fermée (identifiant jti du jeton), supprimée une fois le jeton expiré (purge horaire).
create table jetons_revoques (
    jti varchar(36) not null,
    expire_le timestamp(6) not null,
    revoque_le timestamp(6) not null,
    constraint pk_jetons_revoques primary key (jti)
);

create index idx_jetons_revoques_expire_le on jetons_revoques (expire_le);
