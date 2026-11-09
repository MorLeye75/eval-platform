// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  mesEvaluationsEnseignant,
  publierResultats,
  type EvaluationEnseignantApi,
} from "@/lib/evaluationsEnseignant"
import PublicationResultats from "./PublicationResultats"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
  publierResultats: vi.fn(),
}))

function evaluation(id: number, statut: EvaluationEnseignantApi["statut"], resultatsPublies = false): EvaluationEnseignantApi {
  return {
    id, titre: `Évaluation ${id}`, description: null, dureeStandard: 60, dateOuverture: "2030-01-10T08:00:00",
    dateFermeture: "2030-01-10T18:00:00", statut, resultatsPublies, version: 0, promotionIds: [1],
    nbQuestions: 3, nbCopies: 40, nbAttendus: 45, questions: [], amenagements: [],
  }
}

const TOUTES = [
  evaluation(1, "BROUILLON"),
  evaluation(2, "EN_COURS"),
  evaluation(3, "TERMINEE"),
  evaluation(4, "TERMINEE", true),
  evaluation(5, "TERMINEE"),
]

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <PublicationResultats />
    </QueryClientProvider>
  )
}

describe("publication des résultats (DS-07)", () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue(TOUTES)
    vi.spyOn(window, "confirm").mockReturnValue(true)
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it("ne propose que les évaluations terminées : ni brouillon, ni évaluation en cours", async () => {
    afficher()

    expect(await screen.findByText("Évaluation 3")).toBeTruthy()
    expect(screen.getByText("Évaluation 5")).toBeTruthy()
    expect(screen.queryByText("Évaluation 1")).toBeNull()
    expect(screen.queryByText("Évaluation 2")).toBeNull()
    expect(screen.queryByText("Évaluation 4")).toBeNull() // déjà publiée : onglet « Publiées »
  })

  it("onglet « Publiées » : les évaluations dont les résultats sont publiés, sans bouton de publication", async () => {
    afficher()
    await screen.findByText("Évaluation 3")

    fireEvent.click(screen.getByRole("tab", { name: "Publiées" }))
    fireEvent.click(await screen.findByRole("button", { name: /Évaluation 4/ }))

    expect(screen.getByText(/Résultats déjà publiés/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Publier les résultats/ })).toBeNull()
  })

  it("aucune note d'étudiant inventée n'est affichée", async () => {
    afficher()
    fireEvent.click(await screen.findByRole("button", { name: /Évaluation 3/ }))

    expect(screen.queryByText(/Moussa|Awa|Cheikh/)).toBeNull()
    expect(screen.queryByRole("table")).toBeNull()
    expect(screen.getByText(/40 copies sur 45 attendues/)).toBeTruthy()
  })

  it("la sélection est un bouton à état (aria-pressed) qui se défait au second clic", async () => {
    afficher()
    const carte = await screen.findByRole("button", { name: /Évaluation 3/ })

    fireEvent.click(carte)
    expect(carte.getAttribute("aria-pressed")).toBe("true")
    fireEvent.click(carte)
    expect(carte.getAttribute("aria-pressed")).toBe("false")
  })

  it("publication confirmée : appelle la route de l'évaluation, annonce le bilan et recharge la liste", async () => {
    vi.mocked(publierResultats).mockResolvedValue({
      evaluationId: 3, resultatsPublies: true, nbCopies: 40, nbCopiesNotees: 38, nbCopiesEnAttente: 2,
    })
    afficher()
    fireEvent.click(await screen.findByRole("button", { name: /Évaluation 3/ }))
    const appelsAvant = vi.mocked(mesEvaluationsEnseignant).mock.calls.length

    fireEvent.click(screen.getByRole("button", { name: /Publier les résultats/ }))

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("Évaluation 3"))
    const statut = await screen.findByRole("status")
    expect(statut.textContent).toContain("38 copies notifiées")
    expect(statut.textContent).toContain("2 en attente de notation")
    expect(vi.mocked(publierResultats).mock.calls[0][0]).toBe(3)
    await waitFor(() => expect(vi.mocked(mesEvaluationsEnseignant).mock.calls.length).toBeGreaterThan(appelsAvant))
  })

  it("publication refusée à la confirmation : rien n'est envoyé", async () => {
    vi.mocked(window.confirm).mockReturnValue(false)
    afficher()
    fireEvent.click(await screen.findByRole("button", { name: /Évaluation 3/ }))

    fireEvent.click(screen.getByRole("button", { name: /Publier les résultats/ }))

    expect(publierResultats).not.toHaveBeenCalled()
  })

  it("refus du serveur (marge de tolérance, déjà publiés) : le motif est affiché", async () => {
    vi.mocked(publierResultats).mockRejectedValue(erreurHttp(409, "Des étudiants peuvent encore rendre leur copie : réessayez après la marge de tolérance de 2 minutes suivant la fermeture."))
    afficher()
    fireEvent.click(await screen.findByRole("button", { name: /Évaluation 3/ }))

    fireEvent.click(screen.getByRole("button", { name: /Publier les résultats/ }))

    expect((await screen.findByRole("alert")).textContent).toContain("marge de tolérance")
  })

  it("aucune évaluation à publier : message clair", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(1, "BROUILLON")])
    afficher()

    expect(await screen.findByText("Aucune évaluation terminée en attente de publication.")).toBeTruthy()
  })

  it("serveur injoignable : erreur et nouvel essai", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockRejectedValueOnce(new Error("réseau")).mockResolvedValue(TOUTES)
    afficher()

    const alerte = await screen.findByRole("alert")
    expect(alerte.textContent).toContain("Impossible de charger vos évaluations")
    fireEvent.click(screen.getByRole("button", { name: "Réessayer" }))
    expect(await screen.findByText("Évaluation 3")).toBeTruthy()
  })
})
