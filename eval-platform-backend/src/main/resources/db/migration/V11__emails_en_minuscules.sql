-- Emails en minuscules et sans espaces autour : la connexion ne dépend plus de la casse saisie, et deux comptes
-- ne peuvent plus différer par la seule casse (a@x et A@x). L'application normalise déjà à l'écriture
-- (Utilisateur.setEmail) ; la contrainte garantit la même règle pour une écriture faite hors de l'application.
-- Avec la contrainte unique existante sur email, l'unicité devient insensible à la casse (sans index sur
-- lower(email), qu'H2 ne sait pas créer).
-- Si deux comptes ne diffèrent que par la casse, la mise à jour échoue sur la contrainte unique : à fusionner à la
-- main avant de relancer la migration.
update utilisateurs set email = lower(trim(email)) where email <> lower(trim(email));

alter table utilisateurs add constraint ck_utilisateurs_email_normalise check (email = lower(trim(email)));
