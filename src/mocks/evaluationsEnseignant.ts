import type { Evaluation } from "@/types"

// ============================================================
// Évaluations de l'enseignant connecté — données mockées partagées
// par la liste, la supervision, les statistiques et l'édition.
// À remplacer par :
// const { data } = useQuery({ queryKey: ["enseignant-evaluations"], queryFn: () => api.get("/enseignant/evaluations") })
// ============================================================

export interface EvaluationEnseignantMock extends Evaluation {
  nbSoumissions: number
  nbAttendus: number
  typeLabel: string
}

export const evaluationsEnseignantMock: EvaluationEnseignantMock[] = [
  { id: 1, titre: "Structures de données — QCM",        description: "QCM sur les structures de données fondamentales.",        statut: "EN_COURS",  duree: 45, dateOuverture: "2026-07-10T08:00:00", dateFermeture: "2026-07-12T23:59:00", resultatsPublies: false, nbSoumissions: 32, nbAttendus: 45, typeLabel: "QCM"              },
  { id: 2, titre: "Algorithmique — exercice de code",   description: "Exercice de code sur les algorithmes de tri.",             statut: "BROUILLON", duree: 90, dateOuverture: "2026-07-15T08:00:00", dateFermeture: "2026-07-15T23:59:00", resultatsPublies: false, nbSoumissions: 0,  nbAttendus: 45, typeLabel: "Code"             },
  { id: 3, titre: "Réseaux — questions ouvertes",       description: "Questions ouvertes sur les protocoles réseau.",            statut: "TERMINEE",  duree: 60, dateOuverture: "2026-07-01T08:00:00", dateFermeture: "2026-07-05T23:59:00", resultatsPublies: true,  nbSoumissions: 38, nbAttendus: 38, typeLabel: "Question ouverte" },
  { id: 4, titre: "Bases de données — quiz chronométré",description: "Quiz chronométré sur les bases de données relationnelles.",statut: "PUBLIEE",   duree: 30, dateOuverture: "2026-07-08T08:00:00", dateFermeture: "2026-07-10T18:00:00", resultatsPublies: false, nbSoumissions: 40, nbAttendus: 45, typeLabel: "Quiz"             },
  { id: 5, titre: "Génie logiciel — questions ouvertes",description: "Questions ouvertes sur les principes de génie logiciel.",  statut: "BROUILLON", duree: 60, dateOuverture: "2026-07-20T08:00:00", dateFermeture: "2026-07-20T23:59:00", resultatsPublies: false, nbSoumissions: 0,  nbAttendus: 45, typeLabel: "Question ouverte" },
  { id: 6, titre: "Systèmes d'exploitation — QCM",     description: "QCM sur les concepts des systèmes d'exploitation.",         statut: "TERMINEE",  duree: 45, dateOuverture: "2026-06-20T08:00:00", dateFermeture: "2026-06-20T23:59:00", resultatsPublies: true,  nbSoumissions: 42, nbAttendus: 42, typeLabel: "QCM"              },
]

/** Évaluation de l'enseignant par id (paramètre de route), ou undefined si introuvable. */
export function getEvaluationEnseignant(id: string | number | undefined) {
  if (id === undefined) return undefined
  return evaluationsEnseignantMock.find((e) => e.id === Number(id))
}
