-- DS-16 / DS-14 : un mot de passe attribué par un administrateur (création, import, réinitialisation) est provisoire :
-- le compte ne peut rien faire d'autre que le changer. Les comptes existants ne sont pas concernés.
alter table utilisateurs add column mot_de_passe_provisoire boolean not null default false;
