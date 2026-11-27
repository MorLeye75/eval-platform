-- Verrou optimiste des évaluations (DS-03, DS-04) : compteur incrémenté à chaque modification
alter table evaluations add column version bigint not null default 0;
