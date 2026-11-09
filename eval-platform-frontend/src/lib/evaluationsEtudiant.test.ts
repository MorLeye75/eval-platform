import { describe, expect, it } from "vitest"
import { cheminPassation, etatEvaluation, typeEpreuve } from "@/lib/evaluationsEtudiant"
import type { EvaluationEtudiant } from "@/types"

function evaluation(e: Partial<EvaluationEtudiant> = {}): EvaluationEtudiant {
  return {
    id: 7, titre: "Structures de données", description: null, statut: "PUBLIEE",
    dateOuverture: "2026-11-07T08:00:00", dateFermeture: "2026-11-07T18:00:00",
    dureeMinutes: 30, dureeQuestion: null, nbQuestions: 5, totalPoints: 10, typesQuestions: ["QCM"],
    resultatsPublies: false, soumissionId: null, statutSoumission: null, noteFinale: null, ...e,
  }
}
const pendant = new Date("2026-11-07T10:00:00")

describe("état d'une évaluation pour l'étudiant", () => {
  it("à venir avant l'ouverture, disponible pendant, terminée après la fermeture", () => {
    expect(etatEvaluation(evaluation(), new Date("2026-11-07T07:00:00"))).toBe("a_venir")
    expect(etatEvaluation(evaluation(), pendant)).toBe("disponible")
    expect(etatEvaluation(evaluation(), new Date("2026-11-07T19:00:00"))).toBe("terminee")
  })

  it("copie en cours : disponible (reprise) ; copie remise : terminée même si l'évaluation est ouverte", () => {
    expect(etatEvaluation(evaluation({ statutSoumission: "EN_COURS" }), pendant)).toBe("disponible")
    expect(etatEvaluation(evaluation({ statutSoumission: "SOUMIS" }), pendant)).toBe("terminee")
    expect(etatEvaluation(evaluation({ statut: "TERMINEE" }), pendant)).toBe("terminee")
  })
})

describe("type d'épreuve et écran de passation", () => {
  it.each([
    [["QCM"], "QCM", "/examen/7/epreuve"],
    [["CODE"], "CODE", "/examen/7/epreuve"],
    [["OUVERTE"], "OUVERTE", "/examen/7/epreuve"],
    [["QUIZ_CHRONOMETREE"], "QUIZ", "/examen/7/quiz"],
  ] as const)("%j : %s → %s", (types, type, chemin) => {
    const e = evaluation({ typesQuestions: [...types] })
    expect(typeEpreuve(e)).toBe(type)
    expect(cheminPassation(e)).toBe(chemin)
  })

  it("plusieurs types mélangés : épreuve mixte, sur l'écran unique comme les autres", () => {
    const e = evaluation({ typesQuestions: ["QCM", "OUVERTE"] })
    expect(typeEpreuve(e)).toBe("MIXTE")
    expect(cheminPassation(e)).toBe("/examen/7/epreuve")
  })
})
