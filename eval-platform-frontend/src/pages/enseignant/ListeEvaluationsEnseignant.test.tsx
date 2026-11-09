// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AxiosError, type AxiosResponse } from "axios"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  depublierEvaluation,
  mesEvaluationsEnseignant,
  publierEvaluation,
  publierResultats,
  supprimerEvaluation,
  type EvaluationEnseignantApi,
} from "@/lib/evaluationsEnseignant"
import ListeEvaluationsEnseignant from "./ListeEvaluationsEnseignant"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
  publierEvaluation: vi.fn(),
  depublierEvaluation: vi.fn(),
  supprimerEvaluation: vi.fn(),
  publierResultats: vi.fn(),
}))

function evaluation(id: number, statut: EvaluationEnseignantApi["statut"], extra: Partial<EvaluationEnseignantApi> = {}): EvaluationEnseignantApi {
  return {
    id, titre: `Évaluation ${id}`, description: null, dureeStandard: 60, dateOuverture: "2030-01-10T08:00:00",
    dateFermeture: "2030-01-10T18:00:00", statut, resultatsPublies: false, version: 0, promotionIds: [1],
    nbQuestions: 3, nbCopies: 0, nbAttendus: 45, questions: [], amenagements: [], ...extra,
  }
}

const TOUTES = [
  evaluation(1, "BROUILLON"),
  evaluation(2, "PUBLIEE"),
  evaluation(3, "EN_COURS", { nbCopies: 12 }),
  evaluation(4, "TERMINEE", { nbCopies: 40 }),
]

/** Erreur HTTP telle que renvoyée par le serveur (RFC 7807). */
function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ListeEvaluationsEnseignant />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("Mes évaluations : lecture de l'API et actions (DS-03, DS-04, DS-07)", () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue(TOUTES)
    vi.spyOn(window, "confirm").mockReturnValue(true)
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it("affiche les évaluations du serveur : questions, copies sur attendus, durée et fermeture", async () => {
    afficher()

    const ligne = (await screen.findByText("Évaluation 3")).closest("tr") as HTMLElement
    expect(within(ligne).getByText("3 questions")).toBeTruthy()
    expect(within(ligne).getByText("12")).toBeTruthy()
    expect(within(ligne).getByText("/ 45")).toBeTruthy()
    expect(within(ligne).getByText("60 min")).toBeTruthy()
    expect(screen.getByText("4 sur 4 évaluations")).toBeTruthy()
  })

  it("chargement puis liste ; aucune donnée simulée n'est affichée", async () => {
    afficher()
    expect(screen.getByRole("status").textContent).toContain("Chargement")

    await screen.findByText("Évaluation 1")
    expect(screen.queryByText(/Structures de données/)).toBeNull()
  })

  it("liste vide : invite à créer la première évaluation", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([])
    afficher()

    expect(await screen.findByText("Vous n'avez pas encore créé d'évaluation.")).toBeTruthy()
  })

  it("serveur injoignable : message d'erreur et nouvel essai possible", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockRejectedValueOnce(new Error("réseau")).mockResolvedValue(TOUTES)
    afficher()

    const alerte = await screen.findByRole("alert")
    expect(alerte.textContent).toContain("Impossible de charger vos évaluations")

    fireEvent.click(within(alerte).getByRole("button", { name: "Réessayer" }))
    expect(await screen.findByText("Évaluation 1")).toBeTruthy()
  })

  it("chaque état n'expose que ses actions", async () => {
    afficher()
    await screen.findByText("Évaluation 1")

    // Brouillon : modifier, publier, supprimer
    expect(screen.getByRole("link", { name: "Modifier : Évaluation 1" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Publier : Évaluation 1" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Supprimer : Évaluation 1" })).toBeTruthy()
    // Publiée sans copie : repasser en brouillon, jamais modifier ni supprimer
    expect(screen.getByRole("button", { name: "Repasser en brouillon : Évaluation 2" })).toBeTruthy()
    expect(screen.queryByRole("link", { name: "Modifier : Évaluation 2" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Supprimer : Évaluation 2" })).toBeNull()
    // En cours : supervision seulement
    expect(screen.getByRole("link", { name: "Supervision : Évaluation 3" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Évaluation 3$/ })).toBeNull()
    // Terminée : résultats à publier
    expect(screen.getByRole("button", { name: "Publier les résultats : Évaluation 4" })).toBeTruthy()
    expect(screen.getByText("Résultats non publiés")).toBeTruthy()
  })

  it("une publiée dont un étudiant a commencé ne peut plus être dépubliée", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(2, "PUBLIEE", { nbCopies: 3 })])
    afficher()
    await screen.findByText("Évaluation 2")

    expect(screen.queryByRole("button", { name: /Repasser en brouillon/ })).toBeNull()
  })

  it("publier : appelle l'API, annonce le succès et recharge la liste", async () => {
    vi.mocked(publierEvaluation).mockResolvedValue(evaluation(1, "PUBLIEE"))
    afficher()
    await screen.findByText("Évaluation 1")
    const appelsAvant = vi.mocked(mesEvaluationsEnseignant).mock.calls.length

    fireEvent.click(screen.getByRole("button", { name: "Publier : Évaluation 1" }))

    expect(await screen.findByText("Évaluation publiée.")).toBeTruthy()
    expect(vi.mocked(publierEvaluation).mock.calls[0][0]).toBe(1)
    await waitFor(() => expect(vi.mocked(mesEvaluationsEnseignant).mock.calls.length).toBeGreaterThan(appelsAvant))
  })

  it("publication refusée : le motif du serveur est affiché tel quel", async () => {
    vi.mocked(publierEvaluation).mockRejectedValue(
      erreurHttp(400, "La plage d'ouverture (60 min) est plus courte que la durée maximale de l'épreuve (80 min)")
    )
    afficher()
    await screen.findByText("Évaluation 1")

    fireEvent.click(screen.getByRole("button", { name: "Publier : Évaluation 1" }))

    const alerte = await screen.findByRole("alert")
    expect(alerte.textContent).toContain("plage d'ouverture (60 min)")
  })

  it("repasser en brouillon", async () => {
    vi.mocked(depublierEvaluation).mockResolvedValue(evaluation(2, "BROUILLON"))
    afficher()
    await screen.findByText("Évaluation 2")

    fireEvent.click(screen.getByRole("button", { name: "Repasser en brouillon : Évaluation 2" }))

    await waitFor(() => expect(vi.mocked(depublierEvaluation).mock.calls[0][0]).toBe(2))
    expect(await screen.findByText("Évaluation repassée en brouillon.")).toBeTruthy()
  })

  it("suppression : demande confirmation, rien n'est supprimé si l'enseignant refuse", async () => {
    vi.mocked(window.confirm).mockReturnValue(false)
    afficher()
    await screen.findByText("Évaluation 1")

    fireEvent.click(screen.getByRole("button", { name: "Supprimer : Évaluation 1" }))

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("Évaluation 1"))
    expect(supprimerEvaluation).not.toHaveBeenCalled()
  })

  it("suppression confirmée : le brouillon est supprimé", async () => {
    vi.mocked(supprimerEvaluation).mockResolvedValue(undefined)
    afficher()
    await screen.findByText("Évaluation 1")

    fireEvent.click(screen.getByRole("button", { name: "Supprimer : Évaluation 1" }))

    await waitFor(() => expect(vi.mocked(supprimerEvaluation).mock.calls[0][0]).toBe(1))
    expect(await screen.findByText("Brouillon supprimé.")).toBeTruthy()
  })

  it("publier les résultats : confirmation, puis bilan des copies notifiées et en attente", async () => {
    vi.mocked(publierResultats).mockResolvedValue({
      evaluationId: 4, resultatsPublies: true, nbCopies: 40, nbCopiesNotees: 38, nbCopiesEnAttente: 2,
    })
    afficher()
    await screen.findByText("Évaluation 4")

    fireEvent.click(screen.getByRole("button", { name: "Publier les résultats : Évaluation 4" }))

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("notes et les corrigés"))
    expect(await screen.findByText("Résultats publiés : 38 copies notifiées, 2 en attente de notation.")).toBeTruthy()
    expect(vi.mocked(publierResultats).mock.calls[0][0]).toBe(4)
  })

  it("publication des résultats refusée (marge de tolérance, déjà publiés) : message du serveur", async () => {
    vi.mocked(publierResultats).mockRejectedValue(erreurHttp(409, "Les résultats de cette évaluation sont déjà publiés."))
    afficher()
    await screen.findByText("Évaluation 4")

    fireEvent.click(screen.getByRole("button", { name: "Publier les résultats : Évaluation 4" }))

    expect((await screen.findByRole("alert")).textContent).toContain("déjà publiés")
  })

  it("recherche par titre", async () => {
    afficher()
    await screen.findByText("Évaluation 1")

    fireEvent.change(screen.getByLabelText("Rechercher une évaluation"), { target: { value: "évaluation 3" } })

    expect(screen.queryByText("Évaluation 1")).toBeNull()
    expect(screen.getByText("Évaluation 3")).toBeTruthy()
    expect(screen.getByText("1 sur 4 évaluations")).toBeTruthy()
  })
})
