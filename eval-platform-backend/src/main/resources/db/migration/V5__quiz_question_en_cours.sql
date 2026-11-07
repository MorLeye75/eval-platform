-- Quiz chronométré : question en cours de la copie et heure à laquelle elle a commencé.
-- Nulles tant que l'étudiant n'a pas avancé : question 0, commencée avec la copie (soumissions.date_debut).
-- Le serveur impose l'ordre (pas de retour en arrière) et le temps de chaque question (QuizService).
alter table soumissions add column index_question_quiz integer;
alter table soumissions add column debut_question_quiz timestamp(6);
