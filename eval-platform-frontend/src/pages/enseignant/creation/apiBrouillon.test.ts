import { describe, expect, it } from "vitest"
import type { EvaluationEnseignantApi } from "@/lib/evaluationsEnseignant"
import { versBrouillon, versDateTimeLocal, versRequete } from "./apiBrouillon"
import type { BrouillonEvaluation } from "./brouillon"

const BROUILLON: BrouillonEvaluation = {
  evaluationId: null,
  infos: {
    titre: "  Réseaux — contrôle  ", description: "Contrôle de réseaux", duree: 60,
    dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T12:00", statut: "PUBLIEE", promotionIds: [1, 2],
  },
  questions: [
    { localId: 1, type: "QCM", enonce: " Protocole fiable ? ", points: 2, estChoixMultiple: false,
      choix: [{ id: 1, texte: " TCP ", estCorrect: true }, { id: 2, texte: "UDP", estCorrect: false }] },
    { localId: 2, type: "QUIZ_CHRONOMETREE", enonce: "Port HTTPS ?", points: 1, estChoixMultiple: false, dureeQuestion: 20,
      choix: [{ id: 3, texte: "443", estCorrect: true }, { id: 4, texte: "80", estCorrect: false }] },
    { localId: 3, type: "CODE", enonce: "Somme de deux entiers", points: 5,
      casTests: [
        { id: 5, entree: "2 3", sortieAttendue: "5", estMasque: false },
        { id: 6, entree: "", sortieAttendue: "0", estMasque: true },
      ] },
    { localId: 4, type: "OUVERTE", enonce: "Expliquez le routage", points: 4 },
  ],
}

describe("versRequete : brouillon de l'assistant → corps du POST / PUT (DS-03)", () => {
  const requete = versRequete(BROUILLON)

  it("infos : titre rogné, durée standard, dates et promotions telles quelles", () => {
    expect(requete).toMatchObject({
      titre: "Réseaux — contrôle", description: "Contrôle de réseaux", dureeStandard: 60,
      dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T12:00", promotionIds: [1, 2],
    })
  })

  it("aucun identifiant local ni statut n'est envoyé : le serveur numérote les questions et fixe le statut", () => {
    const texte = JSON.stringify(requete)
    expect(texte).not.toContain("localId")
    expect(texte).not.toContain('"statut"')
    expect(texte).not.toContain('"id"')
    expect(Object.keys(requete)).not.toContain("enseignantId")
  })

  it("QCM : choix rognés, choix unique ; quiz : durée par question en plus", () => {
    expect(requete.questions[0]).toEqual({
      type: "QCM", enonce: "Protocole fiable ?", points: 2, estChoixMultiple: false,
      choix: [{ texte: "TCP", estCorrect: true }, { texte: "UDP", estCorrect: false }],
    })
    expect(requete.questions[1]).toMatchObject({ type: "QUIZ_CHRONOMETREE", dureeQuestion: 20 })
    expect(requete.questions[0]).not.toHaveProperty("dureeQuestion")
  })

  it("code : une entrée vide est envoyée comme absente (entrée facultative), la sortie attendue est conservée", () => {
    expect(requete.questions[2].casTests).toEqual([
      { entree: "2 3", sortieAttendue: "5", estMasque: false },
      { entree: null, sortieAttendue: "0", estMasque: true },
    ])
  })

  it("question ouverte : énoncé et points seulement", () => {
    expect(requete.questions[3]).toEqual({ type: "OUVERTE", enonce: "Expliquez le routage", points: 4 })
  })

  it("l'ordre des questions est leur position dans la liste", () => {
    expect(requete.questions.map((q) => q.type)).toEqual(["QCM", "QUIZ_CHRONOMETREE", "CODE", "OUVERTE"])
  })
})

const DETAIL: EvaluationEnseignantApi = {
  id: 7, titre: "Réseaux", description: null, dureeStandard: 90, dateOuverture: "2030-01-10T08:00:00",
  dateFermeture: "2030-01-10T12:30:00", statut: "BROUILLON", resultatsPublies: false, version: 3, promotionIds: [2],
  nbQuestions: 3, nbCopies: 0, nbAttendus: 45, amenagements: [],
  questions: [
    { id: 30, type: "OUVERTE", enonce: "Troisième", ordre: 3, points: 4, estChoixMultiple: null, dureeQuestion: null, choix: [], casTests: [] },
    { id: 10, type: "QCM", enonce: "Première", ordre: 1, points: 2, estChoixMultiple: true, dureeQuestion: null,
      choix: [{ texte: "A", estCorrect: true }, { texte: "B", estCorrect: true }, { texte: "C", estCorrect: false }], casTests: [] },
    { id: 20, type: "CODE", enonce: "Deuxième", ordre: 2, points: 5, estChoixMultiple: null, dureeQuestion: null, choix: [],
      casTests: [{ entree: null, sortieAttendue: "Bonjour", estMasque: false }, { entree: "Awa", sortieAttendue: "Bonjour Awa", estMasque: true }] },
  ],
}

describe("versBrouillon : détail du serveur → brouillon d'édition", () => {
  const brouillon = versBrouillon(DETAIL)

  it("garde l'identifiant, convertit les dates et reste un brouillon (seul un brouillon est modifiable)", () => {
    expect(brouillon.evaluationId).toBe(7)
    expect(brouillon.infos).toEqual({
      titre: "Réseaux", description: "", duree: 90, dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T12:30",
      statut: "BROUILLON", promotionIds: [2],
    })
  })

  it("questions remises dans l'ordre du serveur, avec des identifiants locaux uniques", () => {
    expect(brouillon.questions.map((q) => q.enonce)).toEqual(["Première", "Deuxième", "Troisième"])
    const ids = brouillon.questions.flatMap((q) => [q.localId, ...(q.choix ?? []).map((c) => c.id), ...(q.casTests ?? []).map((c) => c.id)])
    expect(new Set(ids).size).toBe(ids.length)
  })

  it("QCM à choix multiples et cas de test (entrée absente → champ vide)", () => {
    expect(brouillon.questions[0]).toMatchObject({ type: "QCM", estChoixMultiple: true })
    expect(brouillon.questions[0].choix?.filter((c) => c.estCorrect)).toHaveLength(2)
    expect(brouillon.questions[1].casTests?.map((c) => c.entree)).toEqual(["", "Awa"])
    expect(brouillon.questions[1].casTests?.[1].estMasque).toBe(true)
  })

  it("aller-retour : ré-enregistrer sans rien changer renvoie les mêmes questions au serveur", () => {
    const requete = versRequete(brouillon)
    expect(requete.questions.map((q) => q.enonce)).toEqual(["Première", "Deuxième", "Troisième"])
    expect(requete.questions[1].casTests).toEqual([
      { entree: null, sortieAttendue: "Bonjour", estMasque: false },
      { entree: "Awa", sortieAttendue: "Bonjour Awa", estMasque: true },
    ])
    expect(requete.dureeStandard).toBe(90)
    expect(requete.promotionIds).toEqual([2])
  })
})

describe("versDateTimeLocal", () => {
  it("garde AAAA-MM-JJTHH:mm, valeur d'un champ datetime-local", () => {
    expect(versDateTimeLocal("2030-01-10T08:00:00")).toBe("2030-01-10T08:00")
    expect(versDateTimeLocal("2030-01-10T08:00")).toBe("2030-01-10T08:00")
  })
})
