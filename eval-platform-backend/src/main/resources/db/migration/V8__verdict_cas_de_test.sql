-- Verdict d'un cas de test (DS-13) : réussi, mauvaise réponse, temps dépassé, erreur de compilation ou
-- d'exécution. Seule information montrée à l'étudiant pour un cas masqué. Nul pour les résultats antérieurs.
alter table resultats_cas_tests add column verdict varchar(30);
