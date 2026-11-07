import { describe, expect, it } from "vitest"
import { erreurQuestion, type QuestionLocal } from "./brouillon"

function questionCode(casTests: { entree: string; sortieAttendue: string }[]): QuestionLocal {
  return {
    localId: 1,
    type: "CODE",
    enonce: "Afficher la somme de deux entiers.",
    points: 4,
    casTests: casTests.map((c, i) => ({ id: i + 1, estMasque: false, ...c })),
  } as QuestionLocal
}

describe("erreurQuestion : question de code", () => {
  it("un cas de test sans sortie attendue est signalé (le serveur l'exige)", () => {
    expect(erreurQuestion(questionCode([{ entree: "2 3", sortieAttendue: "5" }, { entree: "1 1", sortieAttendue: "  " }])))
      .toBe("Le cas de test 2 n'a pas de sortie attendue.")
  })

  it("des cas de test complets sont acceptés (l'entrée peut être vide)", () => {
    expect(erreurQuestion(questionCode([{ entree: "", sortieAttendue: "Bonjour" }]))).toBeNull()
  })

  it("aucun cas de test reste refusé", () => {
    expect(erreurQuestion(questionCode([]))).toBe("Ajoutez au moins un cas de test.")
  })
})
