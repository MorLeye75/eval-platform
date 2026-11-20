// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { mesEvaluationsEnseignant, type EvaluationEnseignantApi } from "@/lib/evaluationsEnseignant"
import { comparerEvaluations } from "@/lib/statistiquesEnseignant"
import ComparaisonEvaluations from "./ComparaisonEvaluations"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
}))
vi.mock("@/lib/statistiquesEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/statistiquesEnseignant")>()),
  comparerEvaluations: vi.fn(),
}))

const evaluation = (id: number, statut: EvaluationEnseignantApi["statut"]): EvaluationEnseignantApi => ({
  id, titre: `Évaluation ${id}`, description: null, dureeStandard: 60, dateOuverture: "2030-01-10T08:00:00",
  dateFermeture: "2030-01-10T18:00:00", statut, resultatsPublies: false, version: 0, promotionIds: [1],
  nbQuestions: 3, nbCopies: 40, nbAttendus: 45, questions: [], amenagements: [],
})

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><ComparaisonEvaluations /></QueryClientProvider>)
}

describe("comparaison d'évaluations (DS-19)", () => {
  beforeEach(() => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([
      evaluation(1, "BROUILLON"), evaluation(2, "EN_COURS"), evaluation(3, "TERMINEE"), evaluation(4, "TERMINEE"),
    ])
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("ne propose que les évaluations ouvertes ; « Comparer » attend au moins deux choix", async () => {
    afficher()
    expect(await screen.findByLabelText("Évaluation 3")).toBeTruthy()
    expect(screen.queryByLabelText("Évaluation 1")).toBeNull() // brouillon : aucune copie possible
    const bouton = screen.getByRole("button", { name: /Comparer/ }) as HTMLButtonElement
    expect(bouton.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("Évaluation 2"))
    expect(bouton.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("Évaluation 3"))
    expect(bouton.disabled).toBe(false)
  })

  it("affiche les indicateurs du serveur dans l'ordre choisi ; « — » et aucune barre sans copie notée", async () => {
    vi.mocked(comparerEvaluations).mockResolvedValue({
      evaluations: [
        { evaluationId: 2, titre: "Évaluation 2", statut: "EN_COURS", nbAttendus: 45, nbNotees: 0, moyenneSur20: null, medianeSur20: null, tauxReussite: null },
        { evaluationId: 3, titre: "Évaluation 3", statut: "TERMINEE", nbAttendus: 45, nbNotees: 38, moyenneSur20: 13.4, medianeSur20: 13, tauxReussite: 78 },
      ],
    })
    afficher()
    fireEvent.click(await screen.findByLabelText("Évaluation 2"))
    fireEvent.click(screen.getByLabelText("Évaluation 3"))
    fireEvent.click(screen.getByRole("button", { name: /Comparer/ }))

    await waitFor(() => expect(comparerEvaluations).toHaveBeenCalledWith([2, 3]))
    const tableau = await screen.findByRole("table", { name: "Comparaison des évaluations" })
    const lignes = within(tableau).getAllByRole("row")
    expect(lignes).toHaveLength(3) // en-tête + 2 évaluations
    expect(within(lignes[1]).getByText("Évaluation 2")).toBeTruthy()
    expect(within(lignes[1]).queryByRole("img")).toBeNull()           // pas de barre sans moyenne
    expect(within(lignes[1]).getAllByText("—").length).toBeGreaterThanOrEqual(3)
    expect(within(lignes[2]).getByRole("img", { name: /Moyenne 13,4 \/ 20/ })).toBeTruthy()
    expect(within(lignes[2]).getByText("78 %")).toBeTruthy()
    expect(within(lignes[2]).getByText("38 / 45")).toBeTruthy()
  })

  it("six évaluations au plus : les autres cases sont désactivées", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue(
      Array.from({ length: 8 }, (_, i) => evaluation(i + 1, "TERMINEE")))
    afficher()
    for (let i = 1; i <= 6; i++) fireEvent.click(await screen.findByLabelText(`Évaluation ${i}`))
    expect((screen.getByLabelText("Évaluation 7") as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByLabelText("Évaluation 1") as HTMLInputElement).disabled).toBe(false) // on peut décocher
  })

  it("refus du serveur : son message est affiché, aucun tableau", async () => {
    vi.mocked(comparerEvaluations).mockRejectedValue(erreurHttp(409, "Aucune copie n'est possible avant l'ouverture de l'évaluation."))
    afficher()
    fireEvent.click(await screen.findByLabelText("Évaluation 3"))
    fireEvent.click(screen.getByLabelText("Évaluation 4"))
    fireEvent.click(screen.getByRole("button", { name: /Comparer/ }))
    expect((await screen.findByRole("alert")).textContent).toContain("avant l'ouverture")
    expect(screen.queryByRole("table")).toBeNull()
  })

  it("moins de deux évaluations ouvertes : message clair", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(1, "BROUILLON"), evaluation(2, "TERMINEE")])
    afficher()
    expect(await screen.findByText(/au moins 2 évaluations ouvertes/)).toBeTruthy()
  })
})
