-- Barème négatif optionnel (cahier des charges 4.4) : points retirés pour une mauvaise réponse à un QCM ou à une
-- question de quiz (qui hérite de questions_qcm). 0 par défaut : les évaluations existantes ne changent pas.
-- Une question sans réponse vaut 0 ; la note finale d'une copie n'est jamais négative (calcul dans le service).
alter table questions_qcm add column penalite float(53) not null default 0;
