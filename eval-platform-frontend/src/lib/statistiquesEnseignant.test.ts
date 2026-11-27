// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest"
import { api } from "@/lib/axios"
import {
  exporterResultatsCsv,
  formaterSur20,
  questionsDifficiles,
  statistiquesEvaluation,
  telechargerFichier,
  type QuestionStatApi,
} from "@/lib/statistiquesEnseignant"

vi.mock("@/lib/axios", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

const question = (ordre: number, taux: number | null): QuestionStatApi => ({
  questionId: ordre, ordre, enonce: `Question ${ordre}`, points: 2, moyennePoints: null, tauxReussite: taux,
})

describe("statistiques et export de l'enseignant (DS-09)", () => {
  beforeEach(() => vi.clearAllMocks())

  it("statistiques : bonne route", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { evaluationId: 7 } })
    await statistiquesEvaluation(7)
    expect(api.get).toHaveBeenCalledWith("/enseignant/evaluations/7/statistiques")
  })

  it("export : le fichier est lu en blob", async () => {
    const blob = new Blob(["a;b"])
    vi.mocked(api.get).mockResolvedValue({ data: blob })
    expect(await exporterResultatsCsv(7)).toBe(blob)
    expect(api.get).toHaveBeenCalledWith("/enseignant/evaluations/7/export/csv", { responseType: "blob" })
  })

  it("téléchargement : lien temporaire nommé, libéré après usage", () => {
    const creer = vi.fn().mockReturnValue("blob:x")
    const liberer = vi.fn()
    Object.assign(URL, { createObjectURL: creer, revokeObjectURL: liberer })
    const clic = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})

    telechargerFichier(new Blob(["x"]), "resultats-7.csv")

    expect(clic).toHaveBeenCalledOnce()
    expect(liberer).toHaveBeenCalledWith("blob:x")
  })

  it("affichage sur 20 : « — » sans note, nombre à la française sinon", () => {
    expect(formaterSur20(null)).toBe("—")
    expect(formaterSur20(12.5)).toMatch(/^12[,.]5 \/ 20$/)
  })

  it("questions difficiles : sous 60 %, la plus ratée d'abord, 5 au plus, jamais les questions sans taux", () => {
    const resultat = questionsDifficiles([
      question(1, 90), question(2, 59), question(3, 10), question(4, null), question(5, 60),
      question(6, 20), question(7, 30), question(8, 40), question(9, 50),
    ])
    expect(resultat.map((q) => q.ordre)).toEqual([3, 6, 7, 8, 9])
  })
})
