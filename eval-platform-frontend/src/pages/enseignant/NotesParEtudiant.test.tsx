// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { libelleEtatCopie, resultatsEvaluation, type ResultatsEvaluationApi } from "@/lib/statistiquesEnseignant"
import NotesParEtudiant from "./NotesParEtudiant"

vi.mock("@/lib/statistiquesEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/statistiquesEnseignant")>()),
  resultatsEvaluation: vi.fn(),
}))

const DONNEES: ResultatsEvaluationApi = {
  evaluationId: 3, bareme: 20,
  lignes: [
    { etudiantId: 1, numEtudiant: "ETU-1", nom: "Ba", prenom: "Cheikh", etat: "EVALUE", note: 14.5, noteSurVingt: 14.5, dateRemise: "2030-01-10T10:00:00", soumissionId: 40 },
    { etudiantId: 2, numEtudiant: null, nom: "Diop", prenom: "Moussa", etat: "EN_CORRECTION", note: null, noteSurVingt: null, dateRemise: "2030-01-10T10:05:00", soumissionId: 41 },
    { etudiantId: 3, numEtudiant: "ETU-3", nom: "Sy", prenom: "Awa", etat: "ABSENT", note: null, noteSurVingt: null, dateRemise: null, soumissionId: null },
  ],
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter><NotesParEtudiant evaluationId={3} /></MemoryRouter></QueryClientProvider>)
}

describe("notes par étudiant (DS-09, section C)", () => {
  beforeEach(() => { vi.mocked(resultatsEvaluation).mockResolvedValue(DONNEES) })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("note, état de chaque étudiant ; « — » (jamais 0) sans note ; absent signalé", async () => {
    afficher()
    const ligne = (await screen.findByText("Cheikh Ba")).closest("tr") as HTMLElement
    expect(within(ligne).getByText("Notée")).toBeTruthy()
    expect(within(ligne).getAllByText("14,5").length).toBe(2)
    const attente = screen.getByText("Moussa Diop").closest("tr") as HTMLElement
    expect(within(attente).getByText("En attente")).toBeTruthy()
    expect(within(attente).getAllByText("—").length).toBe(2)
    expect(within(attente).queryByText("0")).toBeNull()
    expect(within(screen.getByText("Awa Sy").closest("tr") as HTMLElement).getByText("Absent")).toBeTruthy()
    expect(screen.getByText("Note / 20")).toBeTruthy()
    expect(resultatsEvaluation).toHaveBeenCalledWith(3)
  })

  it("chaque copie remise mène à la copie complète (relecture, révision) ; un absent n'a pas de lien", async () => {
    afficher()
    const ligne = (await screen.findByText("Cheikh Ba")).closest("tr") as HTMLElement
    expect(within(ligne).getByRole("link", { name: "Voir la copie de Cheikh Ba" }).getAttribute("href")).toBe("/enseignant/copies/40")
    const absent = screen.getByText("Awa Sy").closest("tr") as HTMLElement
    expect(within(absent).queryByRole("link")).toBeNull()
  })

  it("aucun étudiant : message clair", async () => {
    vi.mocked(resultatsEvaluation).mockResolvedValue({ evaluationId: 3, bareme: 20, lignes: [] })
    afficher()
    expect(await screen.findByText(/Aucun étudiant n'est inscrit/)).toBeTruthy()
  })

  it("erreur du serveur : son message est affiché", async () => {
    const response = { status: 403, data: { detail: "Cette évaluation appartient à un autre enseignant." }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
    vi.mocked(resultatsEvaluation).mockRejectedValue(new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response))
    afficher()
    expect(await screen.findByText("Cette évaluation appartient à un autre enseignant.")).toBeTruthy()
  })

  it("libellés d'état connus ; un état inconnu est affiché tel quel", () => {
    expect(libelleEtatCopie("EVALUE")).toBe("Notée")
    expect(libelleEtatCopie("ABSENT")).toBe("Absent")
    expect(libelleEtatCopie("NOUVEAU")).toBe("NOUVEAU")
  })
})
