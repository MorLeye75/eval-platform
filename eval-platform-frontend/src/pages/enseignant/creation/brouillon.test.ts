import { describe, expect, it } from "vitest"
import { basculerChoixMultiple, erreurQuestion, type QuestionLocal } from "./brouillon"

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

function questionQcm(estChoixMultiple: boolean, corrects: boolean[]): QuestionLocal {
  return {
    localId: 2,
    type: "QCM",
    enonce: "Structures linéaires ?",
    points: 2,
    estChoixMultiple,
    choix: corrects.map((estCorrect, i) => ({ id: i + 1, texte: `Choix ${i + 1}`, estCorrect })),
  }
}

describe("QCM : choix unique ou multiple", () => {
  it("repasser en choix unique ne garde que la première bonne réponse", () => {
    const q = questionQcm(true, [false, true, true])

    const patch = basculerChoixMultiple(q, false)

    expect(patch.estChoixMultiple).toBe(false)
    expect(patch.choix?.map((c) => c.estCorrect)).toEqual([false, true, false])
  })

  it("passer en choix multiple ne touche pas aux bonnes réponses", () => {
    expect(basculerChoixMultiple(questionQcm(false, [true, false]), true)).toEqual({ estChoixMultiple: true })
  })

  it("un choix unique avec plusieurs bonnes réponses est refusé (personne ne pourrait le réussir)", () => {
    expect(erreurQuestion(questionQcm(false, [true, true]))).toBe("Un choix unique ne peut avoir qu'une bonne réponse.")
    expect(erreurQuestion(questionQcm(true, [true, true]))).toBeNull()
  })
})
