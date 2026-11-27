// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { lireCopie, reviserNote, type CopieEnseignantApi, type ReponseCopieApi } from "@/lib/copiesEnseignant"
import { noterReponse } from "@/lib/correctionsIA"
import CopieEnseignant from "./CopieEnseignant"

vi.mock("@/lib/copiesEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/copiesEnseignant")>()),
  lireCopie: vi.fn(),
  reviserNote: vi.fn(),
}))
vi.mock("@/lib/correctionsIA", async (original) => ({
  ...(await original<typeof import("@/lib/correctionsIA")>()),
  noterReponse: vi.fn(),
}))

const code = (surcharge: Partial<ReponseCopieApi> = {}): ReponseCopieApi => ({
  reponseId: 11, ordre: 1, type: "CODE", enonce: "Afficher l'entrée", pointsMax: 4, note: 2, notee: true,
  commentaire: "Correction automatique : 1/2 cas de test réussis.", texte: null, code: "print(input().strip() )",
  langage: "python", choix: [],
  casDeTests: [
    { entree: "a", sortieAttendue: "a", sortieObtenue: "a", verdict: "REUSSI", reussi: true, masque: false, messageErreur: null },
    { entree: "b", sortieAttendue: "b", sortieObtenue: "b ", verdict: "MAUVAISE_REPONSE", reussi: false, masque: true, messageErreur: null },
  ],
  revisions: [], ...surcharge,
})

const ouverte: ReponseCopieApi = {
  reponseId: 12, ordre: 2, type: "OUVERTE", enonce: "Que garantit TCP ?", pointsMax: 2, note: 1.5, notee: true,
  commentaire: null, texte: "TCP garantit l'ordre.", code: null, langage: null, choix: [], casDeTests: [], revisions: [],
}

const copie = (surcharge: Partial<CopieEnseignantApi> = {}): CopieEnseignantApi => ({
  soumissionId: 5, evaluationId: 3, evaluation: "Algorithmique", etudiant: "Fatou Sarr", numEtudiant: "ETU-1",
  statut: "EVALUE", noteFinale: 3.5, bareme: 6, resultatsPublies: false, notesModifiables: true,
  dateRemise: "2030-01-10T10:00:00", reponses: [code(), ouverte], ...surcharge,
})

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/enseignant/copies/5"]}>
        <Routes><Route path="/enseignant/copies/:id" element={<CopieEnseignant />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const question = (enonce: string) => screen.getByRole("article", { name: new RegExp(enonce) })

describe("copie d'un étudiant vue par l'enseignant", () => {
  beforeEach(() => { vi.mocked(lireCopie).mockResolvedValue(copie()) })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("affiche l'étudiant, la note finale, le code complet et le verdict de chaque cas de test (cas masqués compris)", async () => {
    afficher()
    expect(await screen.findByRole("heading", { name: /Fatou Sarr/ })).toBeTruthy()
    expect(screen.getByText(/3,5 \/ 6/)).toBeTruthy()
    const q = question("Afficher l'entrée")
    expect(within(q).getByText("print(input().strip() )")).toBeTruthy()
    expect(within(q).getByText("Mauvaise réponse")).toBeTruthy()
    expect(within(q).getByText(/masqué/i)).toBeTruthy()
    expect(within(q).getByText("2 / 4")).toBeTruthy()
    expect(within(question("Que garantit TCP")).getByText("TCP garantit l'ordre.")).toBeTruthy()
    expect(lireCopie).toHaveBeenCalledWith(5)
  })

  it("réviser : le motif est obligatoire, puis la nouvelle note et le motif partent au serveur", async () => {
    vi.mocked(reviserNote).mockResolvedValue({ reponseId: 11, ancienneNote: 2, nouvelleNote: 4, noteFinale: 5.5 })
    afficher()
    const q = await screen.findByRole("article", { name: /Afficher l'entrée/ })
    fireEvent.click(within(q).getByRole("button", { name: /Réviser la note/ }))
    fireEvent.change(within(q).getByLabelText("Nouvelle note"), { target: { value: "4" } })
    fireEvent.click(within(q).getByRole("button", { name: "Enregistrer la révision" }))
    expect(await within(q).findByText(/Le motif est obligatoire/)).toBeTruthy()
    expect(reviserNote).not.toHaveBeenCalled()

    fireEvent.change(within(q).getByLabelText("Motif (montré à l'étudiant)"), { target: { value: "Seul un espace final diffère." } })
    fireEvent.click(within(q).getByRole("button", { name: "Enregistrer la révision" }))
    await waitFor(() => expect(reviserNote).toHaveBeenCalledWith(11, 4, "Seul un espace final diffère."))
  })

  it("une note hors barème est refusée avant l'envoi", async () => {
    afficher()
    const q = await screen.findByRole("article", { name: /Afficher l'entrée/ })
    fireEvent.click(within(q).getByRole("button", { name: /Réviser la note/ }))
    fireEvent.change(within(q).getByLabelText("Nouvelle note"), { target: { value: "9" } })
    fireEvent.change(within(q).getByLabelText("Motif (montré à l'étudiant)"), { target: { value: "Trop" } })
    fireEvent.click(within(q).getByRole("button", { name: "Enregistrer la révision" }))
    expect(await within(q).findByText(/entre 0 et 4/)).toBeTruthy()
    expect(reviserNote).not.toHaveBeenCalled()
  })

  it("l'historique des révisions est affiché : ancienne note, nouvelle note, motif, auteur", async () => {
    vi.mocked(lireCopie).mockResolvedValue(copie({
      reponses: [code({ note: 4, revisions: [{ ancienneNote: 2, nouvelleNote: 4, motif: "Espace final", auteur: "Moussa Ndiaye", reviseeLe: "2030-01-11T09:30:00" }] })],
    }))
    afficher()
    const q = await screen.findByRole("article", { name: /Afficher l'entrée/ })
    const historique = within(q).getByRole("list", { name: "Historique des révisions" })
    expect(within(historique).getByText(/2 → 4/)).toBeTruthy()
    expect(within(historique).getByText(/Espace final/)).toBeTruthy()
    expect(within(historique).getByText(/Moussa Ndiaye/)).toBeTruthy()
  })

  it("résultats publiés : plus aucun bouton de révision, et la raison est dite", async () => {
    vi.mocked(lireCopie).mockResolvedValue(copie({ resultatsPublies: true, notesModifiables: false }))
    afficher()
    expect(await screen.findByText(/Les résultats sont publiés : les notes ne sont plus modifiables/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Réviser la note/ })).toBeNull()
  })

  it("code en repli manuel (non noté) : il se note ici, sans motif", async () => {
    vi.mocked(lireCopie).mockResolvedValue(copie({ statut: "EN_CORRECTION", noteFinale: null,
      reponses: [code({ note: null, notee: false, casDeTests: [], commentaire: null }), ouverte] }))
    vi.mocked(noterReponse).mockResolvedValue({ reponseId: 11, noteRetenue: 3, copieEvaluee: true, noteFinale: 4.5 })
    afficher()
    const q = await screen.findByRole("article", { name: /Afficher l'entrée/ })
    expect(within(q).getByText("À noter")).toBeTruthy()
    expect(within(q).queryByRole("button", { name: /Réviser la note/ })).toBeNull()
    fireEvent.change(within(q).getByLabelText("Note"), { target: { value: "3" } })
    fireEvent.click(within(q).getByRole("button", { name: "Enregistrer la note" }))
    await waitFor(() => expect(noterReponse).toHaveBeenCalledWith(11, 3))
  })

  it("question sans réponse (quiz laissé vide) : « Sans réponse », ni « À noter » ni formulaire", async () => {
    vi.mocked(lireCopie).mockResolvedValue(copie({ noteFinale: 0, reponses: [
      { ...ouverte, reponseId: null, type: "QUIZ_CHRONOMETREE", enonce: "Quelle clause filtre les groupes ?", note: null, notee: false, texte: null },
    ] }))
    afficher()
    const q = await screen.findByRole("article", { name: /Quelle clause filtre les groupes/ })
    expect(within(q).getByText("Sans réponse")).toBeTruthy()
    expect(within(q).queryByText("À noter")).toBeNull()
    expect(within(q).queryByRole("button")).toBeNull()
  })

  it("refus du serveur (409, résultats publiés entre-temps) : son message est affiché", async () => {
    vi.mocked(reviserNote).mockRejectedValue(erreurHttp(409, "Les résultats sont publiés : les notes de cette évaluation ne sont plus modifiables."))
    afficher()
    const q = await screen.findByRole("article", { name: /Afficher l'entrée/ })
    fireEvent.click(within(q).getByRole("button", { name: /Réviser la note/ }))
    fireEvent.change(within(q).getByLabelText("Nouvelle note"), { target: { value: "3" } })
    fireEvent.change(within(q).getByLabelText("Motif (montré à l'étudiant)"), { target: { value: "Relecture" } })
    fireEvent.click(within(q).getByRole("button", { name: "Enregistrer la révision" }))
    expect(await within(q).findByText(/ne sont plus modifiables/)).toBeTruthy()
  })
})
