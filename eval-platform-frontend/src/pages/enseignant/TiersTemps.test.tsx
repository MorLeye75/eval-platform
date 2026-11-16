// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  definirAmenagement,
  erreurDuree,
  listerAmenagements,
  supprimerAmenagement,
  type AmenagementsEvaluationApi,
} from "@/lib/evaluationsEnseignant"
import TiersTemps from "./TiersTemps"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  listerAmenagements: vi.fn(),
  definirAmenagement: vi.fn(),
  supprimerAmenagement: vi.fn(),
}))

const DONNEES: AmenagementsEvaluationApi = {
  evaluationId: 1, dureeStandard: 60, plageMinutes: 150,
  etudiants: [
    { etudiantId: 1, nom: "Ba", prenom: "Cheikh", numEtudiant: "ETU-1", dureeMinutes: null, modifiable: true },
    { etudiantId: 2, nom: "Diop", prenom: "Moussa", numEtudiant: "ETU-2", dureeMinutes: 90, modifiable: true },
    { etudiantId: 3, nom: "Sy", prenom: "Awa", numEtudiant: null, dureeMinutes: null, modifiable: false },
  ],
}

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/enseignant/evaluations/1/tiers-temps"]}>
        <Routes><Route path="/enseignant/evaluations/:id/tiers-temps" element={<TiersTemps />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("tiers-temps d'une évaluation (DS-06)", () => {
  beforeEach(() => {
    vi.mocked(listerAmenagements).mockResolvedValue(DONNEES)
    vi.mocked(definirAmenagement).mockResolvedValue({ etudiantId: 1, dureeMinutes: 80 })
    vi.mocked(supprimerAmenagement).mockResolvedValue(undefined)
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("affiche la durée standard, la plage et la durée accordée à chacun", async () => {
    afficher()
    expect(await screen.findByText("Cheikh Ba")).toBeTruthy()
    expect(screen.getByText(/Durée standard : 60 min/)).toBeTruthy()
    expect(screen.getByText(/\(150 min\)/)).toBeTruthy()
    expect(screen.getAllByText("Standard (60 min)").length).toBe(2)
    expect(screen.getByText("90 min")).toBeTruthy()
    expect(listerAmenagements).toHaveBeenCalledWith("1")
  })

  it("un étudiant dont la copie est commencée est verrouillé : aucun champ proposé", async () => {
    afficher()
    await screen.findByText("Awa Sy")
    expect(screen.getByText(/Verrouillé/)).toBeTruthy()
    expect(screen.queryByLabelText("Durée en minutes : Awa Sy")).toBeNull()
    expect(screen.getByLabelText("Durée en minutes : Cheikh Ba")).toBeTruthy()
  })

  it("enregistre un tiers-temps valide", async () => {
    afficher()
    await screen.findByText("Cheikh Ba")
    fireEvent.change(screen.getByLabelText("Durée en minutes : Cheikh Ba"), { target: { value: "80" } })
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer le tiers-temps : Cheikh Ba" }))
    await waitFor(() => expect(definirAmenagement).toHaveBeenCalledWith(1, 1, 80))
  })

  it("durée trop courte ou au-delà de la plage : message, aucun appel au serveur", async () => {
    afficher()
    await screen.findByText("Cheikh Ba")
    const champ = screen.getByLabelText("Durée en minutes : Cheikh Ba")
    const bouton = screen.getByRole("button", { name: "Enregistrer le tiers-temps : Cheikh Ba" })

    fireEvent.change(champ, { target: { value: "45" } })
    fireEvent.click(bouton)
    expect((await screen.findByRole("alert")).textContent).toContain("minimum : 60 min")

    fireEvent.change(champ, { target: { value: "200" } })
    fireEvent.click(bouton)
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("150 min"))
    expect(definirAmenagement).not.toHaveBeenCalled()
  })

  it("retire un tiers-temps existant", async () => {
    afficher()
    await screen.findByText("Moussa Diop")
    expect(screen.queryByRole("button", { name: "Retirer le tiers-temps : Cheikh Ba" })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Retirer le tiers-temps : Moussa Diop" }))
    await waitFor(() => expect(supprimerAmenagement).toHaveBeenCalledWith(1, 2))
  })

  it("refus du serveur (409) : son message est affiché", async () => {
    vi.mocked(definirAmenagement).mockRejectedValue(erreurHttp(409, "L'étudiant a déjà commencé : son aménagement est verrouillé."))
    afficher()
    await screen.findByText("Cheikh Ba")
    fireEvent.change(screen.getByLabelText("Durée en minutes : Cheikh Ba"), { target: { value: "80" } })
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer le tiers-temps : Cheikh Ba" }))
    expect(await screen.findByText("L'étudiant a déjà commencé : son aménagement est verrouillé.")).toBeTruthy()
  })

  it("chargement impossible : message d'erreur", async () => {
    vi.mocked(listerAmenagements).mockRejectedValue(erreurHttp(403, "Cette évaluation appartient à un autre enseignant."))
    afficher()
    expect(await screen.findByText("Cette évaluation appartient à un autre enseignant.")).toBeTruthy()
  })
})

describe("erreurDuree", () => {
  it("accepte les durées entières entre la durée standard et la plage, bornes comprises", () => {
    expect(erreurDuree("60", 60, 150)).toBeNull()
    expect(erreurDuree("150", 60, 150)).toBeNull()
    expect(erreurDuree("80", 60, 150)).toBeNull()
  })

  it("refuse le vide, les décimaux, le trop court et le trop long", () => {
    expect(erreurDuree("", 60, 150)).toContain("entier")
    expect(erreurDuree("  ", 60, 150)).toContain("entier")
    expect(erreurDuree("80.5", 60, 150)).toContain("entier")
    expect(erreurDuree("59", 60, 150)).toContain("minimum : 60")
    expect(erreurDuree("151", 60, 150)).toContain("150 min")
  })
})
