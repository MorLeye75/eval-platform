import { beforeEach, describe, expect, it, vi } from "vitest"
import { api } from "@/lib/axios"
import {
  actionsDisponibles,
  creerEvaluation,
  definirAmenagement,
  depublierEvaluation,
  detailEvaluationEnseignant,
  libelleNbQuestions,
  listerPromotions,
  mesEvaluationsEnseignant,
  modifierEvaluation,
  publierEvaluation,
  publierResultats,
  supprimerAmenagement,
  supprimerEvaluation,
  type EvaluationRequete,
} from "@/lib/evaluationsEnseignant"

vi.mock("@/lib/axios", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

const REQUETE: EvaluationRequete = {
  titre: "Réseaux", description: "d", dureeStandard: 60, dateOuverture: "2030-01-10T08:00",
  dateFermeture: "2030-01-10T12:00", promotionIds: [1], questions: [],
}

describe("appels de l'espace enseignant (DS-03 à DS-07)", () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.get).mockResolvedValue({ data: "réponse" })
    vi.mocked(api.post).mockResolvedValue({ data: "réponse" })
    vi.mocked(api.put).mockResolvedValue({ data: "réponse" })
    vi.mocked(api.delete).mockResolvedValue({ data: undefined })
  })

  it("liste des promotions et des évaluations", async () => {
    expect(await listerPromotions()).toBe("réponse")
    expect(api.get).toHaveBeenCalledWith("/promotions")

    expect(await mesEvaluationsEnseignant()).toBe("réponse")
    expect(api.get).toHaveBeenCalledWith("/enseignant/evaluations")
  })

  it("détail d'une évaluation", async () => {
    await detailEvaluationEnseignant(7)
    expect(api.get).toHaveBeenCalledWith("/enseignant/evaluations/7")
  })

  it("création : POST du corps tel quel, jamais d'identifiant d'enseignant", async () => {
    await creerEvaluation(REQUETE)
    expect(api.post).toHaveBeenCalledWith("/enseignant/evaluations", REQUETE)
    expect(Object.keys(vi.mocked(api.post).mock.calls[0][1] as object)).not.toContain("enseignantId")
  })

  it("modification : PUT sur l'identifiant ; suppression : DELETE", async () => {
    await modifierEvaluation(7, REQUETE)
    expect(api.put).toHaveBeenCalledWith("/enseignant/evaluations/7", REQUETE)

    await supprimerEvaluation(7)
    expect(api.delete).toHaveBeenCalledWith("/enseignant/evaluations/7")
  })

  it("publication, dépublication et publication des résultats", async () => {
    await publierEvaluation(7)
    expect(api.post).toHaveBeenCalledWith("/enseignant/evaluations/7/publier")

    await depublierEvaluation(7)
    expect(api.post).toHaveBeenCalledWith("/enseignant/evaluations/7/depublier")

    await publierResultats(7)
    expect(api.post).toHaveBeenCalledWith("/enseignant/evaluations/7/resultats/publier")
  })

  it("tiers-temps : PUT avec la durée, DELETE", async () => {
    await definirAmenagement(7, 42, 80)
    expect(api.put).toHaveBeenCalledWith("/enseignant/evaluations/7/amenagements/42", { dureeMinutes: 80 })

    await supprimerAmenagement(7, 42)
    expect(api.delete).toHaveBeenCalledWith("/enseignant/evaluations/7/amenagements/42")
  })

  it("une erreur du serveur n'est pas avalée : elle remonte à l'écran qui la présente", async () => {
    vi.mocked(api.post).mockRejectedValue(new Error("409"))
    await expect(publierEvaluation(7)).rejects.toThrow("409")
  })
})

describe("actionsDisponibles : ce qui est proposé selon l'état de l'évaluation", () => {
  const e = (statut: "BROUILLON" | "PUBLIEE" | "EN_COURS" | "TERMINEE", nbCopies: number | null = 0, resultatsPublies = false) =>
    actionsDisponibles({ statut, nbCopies, resultatsPublies })

  it("brouillon : modifier, publier, supprimer ; rien d'autre", () => {
    expect(e("BROUILLON")).toEqual({
      modifier: true, supprimer: true, publier: true, depublier: false,
      supervision: false, statistiques: false, publierResultats: false,
    })
  })

  it("publiée : repasser en brouillon seulement tant qu'aucune copie n'existe", () => {
    expect(e("PUBLIEE", 0).depublier).toBe(true)
    expect(e("PUBLIEE", 3).depublier).toBe(false)
    expect(e("PUBLIEE", null).depublier).toBe(true)
    expect(e("PUBLIEE").modifier).toBe(false)
    expect(e("PUBLIEE").supprimer).toBe(false)
  })

  it("en cours : supervision, jamais de modification ni de dépublication", () => {
    const a = e("EN_COURS", 12)
    expect(a.supervision).toBe(true)
    expect(a.modifier || a.supprimer || a.publier || a.depublier).toBe(false)
  })

  it("terminée : résultats publiables une seule fois", () => {
    expect(e("TERMINEE", 40, false).publierResultats).toBe(true)
    expect(e("TERMINEE", 40, true).publierResultats).toBe(false)
    expect(e("EN_COURS", 40, false).publierResultats).toBe(false)
    expect(e("TERMINEE").statistiques).toBe(true)
  })
})

describe("libelleNbQuestions", () => {
  it("accorde le pluriel", () => {
    expect(libelleNbQuestions(0)).toBe("0 question")
    expect(libelleNbQuestions(1)).toBe("1 question")
    expect(libelleNbQuestions(5)).toBe("5 questions")
  })
})
