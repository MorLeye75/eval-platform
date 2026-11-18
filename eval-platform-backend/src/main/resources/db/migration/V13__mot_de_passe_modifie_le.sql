-- DS-16 : date du dernier mot de passe provisoire attribué par un administrateur.
-- Les jetons émis avant cette date sont refusés (révocation des sessions à la réinitialisation).
alter table utilisateurs add column mot_de_passe_modifie_le timestamp;
