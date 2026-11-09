// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AxiosError, type AxiosResponse } from "axios"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { CLE_BROUILLON_EVALUATION } from "@/lib/session"
import {
  detailEvaluationEnseignant,
  listerPromotions,
  type EvaluationEnseignantApi,
} from "@/lib/evaluationsEnseignant"
import InfosGenerales from "./InfosGenerales"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  listerPromotions: vi.fn(),
  detailEvaluationEnseignant: vi.fn(),
}))

const PROMOTIONS = [
  { id: 1, nom: "DIC2 Informatique", annee: "2025/2026", nbEtudiants: 38 },
  { id: 2, nom: "DIC1 Informatique", annee: "2025/2026", nbEtudiants: 1 },
]

const DETAIL: EvaluationEnseignantApi = {
  id: 5, titre: "Réseaux — contrôle", description: "Contrôle de réseaux", dureeStandard: 90,
  dateOuverture: "2030-01-10T08:00:00", dateFermeture: "2030-01-10T12:30:00", statut: "BROUILLON",
  resultatsPublies: false, version: 2, promotionIds: [1], nbQuestions: 1, nbCopies: 0, nbAttendus: 38, amenagements: [],
  questions: [{ id: 9, type: "OUVERTE", enonce: "Expliquez TCP", ordre: 1, points: 4, estChoixMultiple: null, dureeQuestion: null, choix: [], casTests: [] }],
}

function erreurHttp(status: number) {
  const response = { status, data: {}, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher(chemin: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[chemin]}>
        <Routes>
          <Route path="/enseignant/evaluations/creation" element={<InfosGenerales />} />
          <Route path="/enseignant/evaluations/creation/questions" element={<p>Étape 2</p>} />
          <Route path="/enseignant/evaluations/:id/edition" element={<InfosGenerales />} />
          <Route path="/enseignant/evaluations/:id/edition/questions" element={<p>Étape 2 (édition)</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const brouillonGarde = () => JSON.parse(sessionStorage.getItem(CLE_BROUILLON_EVALUATION) ?? "null")

describe("étape 1 de l'assistant : promotions et chargement d'une évaluation à modifier (DS-03)", () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.resetAllMocks()
    vi.mocked(listerPromotions).mockResolvedValue(PROMOTIONS)
  })

  afterEach(() => {
    cleanup()
  })

  it("propose les promotions du serveur avec leur effectif", async () => {
    afficher("/enseignant/evaluations/creation")

    expect(await screen.findByText("DIC2 Informatique")).toBeTruthy()
    expect(screen.getByText("2025/2026 · 38 étudiants")).toBeTruthy()
    expect(screen.getByText("2025/2026 · 1 étudiant")).toBeTruthy()
    expect(screen.getAllByRole("checkbox")).toHaveLength(2)
  })

  it("on ne peut pas passer à l'étape suivante sans promotion", async () => {
    afficher("/enseignant/evaluations/creation")
    await screen.findByText("DIC2 Informatique")
    fireEvent.change(screen.getByPlaceholderText(/Ex : Algorithmique/), { target: { value: "Titre" } })

    fireEvent.click(screen.getByRole("button", { name: /Suivant/ }))

    expect(screen.getByText("Choisissez au moins une promotion.")).toBeTruthy()
    expect(screen.queryByText("Étape 2")).toBeNull()
  })

  it("cocher puis décocher une promotion met à jour le brouillon conservé", async () => {
    afficher("/enseignant/evaluations/creation")
    const cases = await screen.findAllByRole("checkbox")

    fireEvent.click(cases[0])
    await waitFor(() => expect(brouillonGarde().infos.promotionIds).toEqual([1]))
    fireEvent.click(cases[1])
    await waitFor(() => expect(brouillonGarde().infos.promotionIds).toEqual([1, 2]))
    fireEvent.click(cases[0])
    await waitFor(() => expect(brouillonGarde().infos.promotionIds).toEqual([2]))
  })

  it("promotions indisponibles : message et nouvel essai", async () => {
    vi.mocked(listerPromotions).mockRejectedValueOnce(new Error("réseau")).mockResolvedValue(PROMOTIONS)
    afficher("/enseignant/evaluations/creation")

    const alerte = await screen.findByRole("alert")
    expect(alerte.textContent).toContain("Impossible de charger les promotions")

    fireEvent.click(screen.getByRole("button", { name: "Réessayer" }))
    expect(await screen.findByText("DIC2 Informatique")).toBeTruthy()
  })

  it("édition : le formulaire est rempli depuis le serveur, promotion cochée comprise", async () => {
    vi.mocked(detailEvaluationEnseignant).mockResolvedValue(DETAIL)
    afficher("/enseignant/evaluations/5/edition")

    expect(screen.getByRole("status").textContent).toContain("Chargement de l'évaluation")
    expect(await screen.findByDisplayValue("Réseaux — contrôle")).toBeTruthy()
    expect(screen.getByDisplayValue("90")).toBeTruthy()
    expect(screen.getByDisplayValue("2030-01-10T12:30")).toBeTruthy()
    const [dic2, dic1] = await screen.findAllByRole("checkbox")
    await waitFor(() => expect(dic2.getAttribute("aria-checked")).toBe("true"))
    expect(dic1.getAttribute("aria-checked")).toBe("false")
    expect(vi.mocked(detailEvaluationEnseignant).mock.calls[0][0]).toBe(5)
  })

  it("édition : un brouillon local de cet onglet passe avant le serveur (rien n'est écrasé)", async () => {
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify({
      evaluationId: 5,
      infos: { titre: "Titre modifié, pas encore enregistré", description: "d", duree: 60, dateOuverture: "2030-01-10T08:00",
        dateFermeture: "2030-01-10T12:00", statut: "BROUILLON", promotionIds: [2] },
      questions: [],
    }))
    afficher("/enseignant/evaluations/5/edition")

    expect(await screen.findByDisplayValue("Titre modifié, pas encore enregistré")).toBeTruthy()
    expect(detailEvaluationEnseignant).not.toHaveBeenCalled()
  })

  it("édition d'une évaluation inconnue (404) ou d'un autre enseignant (403) : même message", async () => {
    for (const statut of [404, 403]) {
      cleanup()
      sessionStorage.clear()
      vi.mocked(detailEvaluationEnseignant).mockRejectedValue(erreurHttp(statut))
      afficher("/enseignant/evaluations/99/edition")

      expect(await screen.findByText("Cette évaluation est introuvable.")).toBeTruthy()
    }
  })

  it("édition, serveur en panne : erreur de chargement, pas « introuvable »", async () => {
    vi.mocked(detailEvaluationEnseignant).mockRejectedValue(erreurHttp(500))
    afficher("/enseignant/evaluations/5/edition")

    expect((await screen.findByRole("alert")).textContent).toContain("Impossible de charger l'évaluation")
    expect(screen.queryByText("Cette évaluation est introuvable.")).toBeNull()
  })

  it("édition d'une évaluation déjà publiée : l'enseignant est prévenu avant de retaper quoi que ce soit", async () => {
    vi.mocked(detailEvaluationEnseignant).mockResolvedValue({ ...DETAIL, statut: "PUBLIEE" })
    afficher("/enseignant/evaluations/5/edition")

    expect(await screen.findByText(/Seul un brouillon peut être modifié/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Suivant/ })).toBeNull()
  })

  it("le chargement ne remplace pas un brouillon local par un formulaire vide en attendant le serveur", async () => {
    let terminer: (e: EvaluationEnseignantApi) => void = () => {}
    vi.mocked(detailEvaluationEnseignant).mockReturnValue(new Promise((resolve) => { terminer = resolve }))
    afficher("/enseignant/evaluations/5/edition")
    await screen.findByRole("status")

    expect(brouillonGarde()).toBeNull() // rien d'écrit tant que le détail n'est pas arrivé

    terminer(DETAIL)
    await waitFor(() => expect(brouillonGarde()?.infos.titre).toBe("Réseaux — contrôle"))
  })
})
