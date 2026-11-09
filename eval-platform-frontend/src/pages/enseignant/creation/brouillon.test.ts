import { describe, expect, it } from "vitest"
import {
  basculerChoixMultiple,
  erreurQuestion,
  validerInfos,
  type InfosGeneralesForm,
  type QuestionLocal,
} from "./brouillon"

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

function infos(extra: Partial<InfosGeneralesForm> = {}): InfosGeneralesForm {
  return {
    titre: "Réseaux", description: "Contrôle", duree: 60, dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T12:00",
    statut: "BROUILLON", promotionIds: [1], ...extra,
  }
}

describe("validerInfos : promotions et plage d'ouverture (DS-03, DS-04)", () => {
  it("des informations complètes sont acceptées", () => {
    expect(validerInfos(infos())).toEqual({})
  })

  it("au moins une promotion est obligatoire (le serveur la refuse sinon)", () => {
    expect(validerInfos(infos({ promotionIds: [] })).promotionIds).toBe("Choisissez au moins une promotion.")
  })

  it("à publier : une plage plus courte que la durée est signalée avant l'envoi (le serveur refuserait la publication)", () => {
    const erreurs = validerInfos(infos({ statut: "PUBLIEE", duree: 90, dateFermeture: "2030-01-10T09:00" }))
    expect(erreurs.dateFermeture).toContain("La plage d'ouverture (60 min) est plus courte que la durée (90 min)")
  })

  it("à publier : une durée égale à la plage est acceptée", () => {
    expect(validerInfos(infos({ statut: "PUBLIEE", duree: 240 }))).toEqual({})
  })

  it("en brouillon : la plage n'est pas exigée, les dates se règlent souvent en dernier", () => {
    expect(validerInfos(infos({ statut: "BROUILLON", duree: 90, dateFermeture: "2030-01-10T09:00" }))).toEqual({})
  })

  it("une fermeture avant l'ouverture reste refusée, sans message de plage en plus", () => {
    const erreurs = validerInfos(infos({ statut: "PUBLIEE", dateFermeture: "2030-01-10T07:00" }))
    expect(erreurs.dateFermeture).toBe("La fermeture doit être après l'ouverture.")
  })
})
