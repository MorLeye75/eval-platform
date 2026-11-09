// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AxiosError, type AxiosResponse } from "axios"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { CLE_BROUILLON_EVALUATION } from "@/lib/session"
import {
  creerEvaluation,
  modifierEvaluation,
  publierEvaluation,
  type EvaluationEnseignantApi,
} from "@/lib/evaluationsEnseignant"
import Previsualisation from "./Previsualisation"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  creerEvaluation: vi.fn(),
  modifierEvaluation: vi.fn(),
  publierEvaluation: vi.fn(),
}))

function brouillon(statut: "BROUILLON" | "PUBLIEE", evaluationId: number | null = null) {
  return {
    evaluationId,
    infos: {
      titre: "Structures de données", description: "QCM sur les piles et files.", duree: 60,
      dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T18:00", statut, promotionIds: [1, 2],
    },
    questions: [
      {
        localId: 1, type: "QCM", enonce: "Structure LIFO ?", points: 2, estChoixMultiple: false,
        choix: [{ id: 1, texte: "Pile", estCorrect: true }, { id: 2, texte: "File", estCorrect: false }],
      },
      {
        localId: 2, type: "CODE", enonce: "Afficher bonjour", points: 3,
        casTests: [{ id: 3, entree: "", sortieAttendue: "Bonjour", estMasque: false }],
      },
    ],
  }
}

const reponseApi = (id: number, statut: EvaluationEnseignantApi["statut"] = "BROUILLON") => ({ id, statut }) as EvaluationEnseignantApi

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher(b: ReturnType<typeof brouillon>) {
  sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify(b))
  const chemin = b.evaluationId === null
    ? "/enseignant/evaluations/creation/previsualisation"
    : `/enseignant/evaluations/${b.evaluationId}/edition/previsualisation`
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[chemin]}>
        <Routes>
          <Route path="/enseignant/evaluations/creation/previsualisation" element={<Previsualisation />} />
          <Route path="/enseignant/evaluations/:id/edition/previsualisation" element={<Previsualisation />} />
          <Route path="/enseignant/evaluations" element={<p>Liste des évaluations</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const brouillonGarde = () => JSON.parse(sessionStorage.getItem(CLE_BROUILLON_EVALUATION) ?? "null")

describe("enregistrement d'une évaluation depuis la prévisualisation (DS-03, DS-04)", () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.resetAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  it("brouillon : un seul POST, jamais de publication, puis retour à la liste et brouillon local effacé", async () => {
    vi.mocked(creerEvaluation).mockResolvedValue(reponseApi(7))
    afficher(brouillon("BROUILLON"))

    fireEvent.click(screen.getByRole("button", { name: /Enregistrer en brouillon/ }))

    expect(await screen.findByText("Liste des évaluations")).toBeTruthy()
    expect(creerEvaluation).toHaveBeenCalledTimes(1)
    expect(publierEvaluation).not.toHaveBeenCalled()
    expect(brouillonGarde()).toBeNull()
  })

  it("le corps envoyé reprend promotions, durée, questions et cas de test (entrée vide = absente)", async () => {
    vi.mocked(creerEvaluation).mockResolvedValue(reponseApi(7))
    afficher(brouillon("BROUILLON"))

    fireEvent.click(screen.getByRole("button", { name: /Enregistrer en brouillon/ }))
    await screen.findByText("Liste des évaluations")

    const requete = vi.mocked(creerEvaluation).mock.calls[0][0]
    expect(requete).toMatchObject({
      titre: "Structures de données", dureeStandard: 60, promotionIds: [1, 2],
      dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T18:00",
    })
    expect(requete.questions).toHaveLength(2)
    expect(requete.questions[1].casTests).toEqual([{ entree: null, sortieAttendue: "Bonjour", estMasque: false }])
  })

  it("« Publier immédiatement » : création puis publication de l'évaluation créée", async () => {
    vi.mocked(creerEvaluation).mockResolvedValue(reponseApi(7))
    vi.mocked(publierEvaluation).mockResolvedValue(reponseApi(7, "PUBLIEE"))
    afficher(brouillon("PUBLIEE"))

    fireEvent.click(screen.getByRole("button", { name: /Publier l'évaluation/ }))

    expect(await screen.findByText("Liste des évaluations")).toBeTruthy()
    expect(vi.mocked(publierEvaluation).mock.calls[0][0]).toBe(7)
    expect(brouillonGarde()).toBeNull()
  })

  it("édition : PUT sur l'évaluation existante, jamais de création", async () => {
    vi.mocked(modifierEvaluation).mockResolvedValue(reponseApi(5))
    afficher(brouillon("BROUILLON", 5))

    fireEvent.click(screen.getByRole("button", { name: /Enregistrer en brouillon/ }))

    await screen.findByText("Liste des évaluations")
    expect(vi.mocked(modifierEvaluation).mock.calls[0][0]).toBe(5)
    expect(creerEvaluation).not.toHaveBeenCalled()
  })

  it("création refusée par le serveur : le motif est affiché et le brouillon local est conservé", async () => {
    vi.mocked(creerEvaluation).mockRejectedValue(erreurHttp(400, "Question 2 : chaque cas de test doit avoir une sortie attendue non vide."))
    afficher(brouillon("BROUILLON"))

    fireEvent.click(screen.getByRole("button", { name: /Enregistrer en brouillon/ }))

    const alerte = await screen.findByRole("alert")
    expect(alerte.textContent).toContain("Question 2")
    expect(brouillonGarde()).not.toBeNull()
    expect(screen.queryByText("Liste des évaluations")).toBeNull()
    // Le bouton se libère : l'enseignant peut corriger et réessayer
    expect((screen.getByRole("button", { name: /Enregistrer en brouillon/ }) as HTMLButtonElement).disabled).toBe(false)
  })

  it("serveur injoignable : message générique, brouillon conservé", async () => {
    vi.mocked(creerEvaluation).mockRejectedValue(new AxiosError("Network Error", "ERR_NETWORK"))
    afficher(brouillon("BROUILLON"))

    fireEvent.click(screen.getByRole("button", { name: /Enregistrer en brouillon/ }))

    expect((await screen.findByRole("alert")).textContent).toContain("L'enregistrement a échoué")
    expect(brouillonGarde()).not.toBeNull()
  })

  it("publication refusée après création : le brouillon est créé, le motif affiché, et un nouvel essai le modifie au lieu d'en créer un second", async () => {
    vi.mocked(creerEvaluation).mockResolvedValue(reponseApi(7))
    vi.mocked(publierEvaluation)
      .mockRejectedValueOnce(erreurHttp(400, "La plage d'ouverture (60 min) est plus courte que la durée maximale (90 min)."))
      .mockResolvedValue(reponseApi(7, "PUBLIEE"))
    vi.mocked(modifierEvaluation).mockResolvedValue(reponseApi(7))
    afficher(brouillon("PUBLIEE"))

    fireEvent.click(screen.getByRole("button", { name: /Publier l'évaluation/ }))

    const alerte = await screen.findByRole("alert")
    expect(alerte.textContent).toContain("Le brouillon est enregistré")
    expect(alerte.textContent).toContain("plage d'ouverture (60 min)")
    await waitFor(() => expect(brouillonGarde()?.evaluationId).toBe(7))

    fireEvent.click(screen.getByRole("button", { name: /Publier l'évaluation/ }))

    await screen.findByText("Liste des évaluations")
    expect(creerEvaluation).toHaveBeenCalledTimes(1)
    expect(vi.mocked(modifierEvaluation).mock.calls[0][0]).toBe(7)
    expect(publierEvaluation).toHaveBeenCalledTimes(2)
  })

  it("un brouillon sans promotion ou sans question est renvoyé à l'étape qui manque", () => {
    const b = brouillon("BROUILLON")
    b.infos.promotionIds = []
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify(b))
    const client = new QueryClient()
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={["/enseignant/evaluations/creation/previsualisation"]}>
          <Routes>
            <Route path="/enseignant/evaluations/creation/previsualisation" element={<Previsualisation />} />
            <Route path="/enseignant/evaluations/creation" element={<p>Étape 1</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    )

    expect(screen.getByText("Étape 1")).toBeTruthy()
    expect(creerEvaluation).not.toHaveBeenCalled()
  })
})
