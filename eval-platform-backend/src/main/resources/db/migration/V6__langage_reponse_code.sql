-- Langage choisi par l'étudiant pour une réponse de code (c, cpp, java, python, javascript) :
-- nécessaire pour exécuter le code à la correction. Nul pour les réponses enregistrées avant cette migration.
alter table reponses_code add column langage varchar(20);
