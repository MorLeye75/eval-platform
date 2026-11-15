// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  genererPropositionsIA,
  listerCorrectionsIA,
  rejeterProposition,
  validerProposition,
  type PropositionIAApi,
} from "@/lib/correctionsIA"
import { mesEvaluationsEnseignant, type EvaluationEnseignantApi } from "@/lib/evaluationsEnseignant"
import ValidationIA from "./ValidationIA"

vi.mock("@/lib/correctionsIA", async (original) => ({
  ...(await original<typeof import("@/lib/correctionsIA")>()),
  genererPropositionsIA: vi.fn(),
  listerCorrectionsIA: vi.fn(),
  validerProposition: vi.fn(),
  rejeterProposition: vi.fn(),
}))
vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
}))

const proposition = (id: number, surcharge: Partial<PropositionIAApi> = {}): PropositionIAApi => ({
  id, reponseId: id * 10, evaluationId: 7, evaluation: "Génie logiciel", etudiant: "Moussa Diop",
  question: "Expliquez la différence entre agrégation et composition.", reponse: "L'agrégation est faible.",
  pointsMax: 5, noteProposee: 4.5, justification: "Réponse complète.", statut: "EN_ATTENTE", noteRetenue: null, ...surcharge,
})

const terminee = { id: 7, titre: "Génie logiciel", statut: "TERMINEE" } as EvaluationEnseignantApi

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><ValidationIA /></QueryClientProvider>)
}

async function ouvrirLaPremiere() {
  fireEvent.click((await screen.findAllByRole("button", { expanded: false }))[0])
}

describe("validation des corrections IA (DS-08)", () => {
  beforeEach(() => {
    vi.mocked(listerCorrectionsIA).mockResolvedValue({ propositions: [proposition(1), proposition(2, { statut: "VALIDEE", noteRetenue: 4 })], enCours: 0 })
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([terminee])
    vi.mocked(validerProposition).mockResolvedValue({ reponseId: 10, noteRetenue: 4.5, copieEvaluee: false, noteFinale: null })
    vi.mocked(rejeterProposition).mockResolvedValue({ reponseId: 10, noteRetenue: 2, copieEvaluee: true, noteFinale: 12.5 })
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("affiche les propositions de l'API (en attente par défaut), pas de données simulées", async () => {
    afficher()
    expect(await screen.findByText("Moussa Diop")).toBeTruthy()
    expect(screen.getByText("1 en attente")).toBeTruthy()
  })

  it("valider sans saisie : appelle la route sans note (la note de l'IA est reprise)", async () => {
    afficher()
    await ouvrirLaPremiere()
    fireEvent.click(screen.getByRole("button", { name: /Valider/ }))
    await waitFor(() => expect(validerProposition).toHaveBeenCalledWith(1, undefined))
    expect(await screen.findByText("Note enregistrée.")).toBeTruthy()
  })

  it("valider avec une note : elle est envoyée ; hors barème : refusée sans appel", async () => {
    afficher()
    await ouvrirLaPremiere()
    const champ = screen.getByLabelText("Note finale :")
    fireEvent.change(champ, { target: { value: "9" } })
    fireEvent.click(screen.getByRole("button", { name: /Valider/ }))
    expect(await screen.findByText("La note doit être comprise entre 0 et 5.")).toBeTruthy()
    expect(validerProposition).not.toHaveBeenCalled()

    fireEvent.change(champ, { target: { value: "3.5" } })
    fireEvent.click(screen.getByRole("button", { name: /Valider/ }))
    await waitFor(() => expect(validerProposition).toHaveBeenCalledWith(1, 3.5))
  })

  it("rejeter sans note : refusé côté écran ; avec note : envoyée, et la fin de copie est annoncée", async () => {
    afficher()
    await ouvrirLaPremiere()
    fireEvent.click(screen.getByRole("button", { name: /Rejeter/ }))
    expect(await screen.findByText("Saisissez votre note pour rejeter la proposition.")).toBeTruthy()
    expect(rejeterProposition).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText("Note finale :"), { target: { value: "2" } })
    fireEvent.click(screen.getByRole("button", { name: /Rejeter/ }))
    await waitFor(() => expect(rejeterProposition).toHaveBeenCalledWith(1, 2))
    expect(await screen.findByText("Note enregistrée. Copie terminée : note finale 12.5.")).toBeTruthy()
  })

  it("erreur du serveur (409) : le message du serveur est affiché", async () => {
    vi.mocked(validerProposition).mockRejectedValue(erreurHttp(409, "Cette proposition a déjà été traitée."))
    afficher()
    await ouvrirLaPremiere()
    fireEvent.click(screen.getByRole("button", { name: /Valider/ }))
    expect(await screen.findByText("Cette proposition a déjà été traitée.")).toBeTruthy()
  })

  it("générer : seules les évaluations terminées sont proposées ; le nombre de propositions lancées est annoncé", async () => {
    vi.mocked(genererPropositionsIA).mockResolvedValue({ evaluationId: 7, nbReponsesAProposer: 3 })
    afficher()
    const choix = await screen.findByLabelText("Évaluation terminée :")
    await screen.findByRole("option", { name: "Génie logiciel" })
    fireEvent.change(choix, { target: { value: "7" } })
    fireEvent.click(screen.getByRole("button", { name: /Générer les propositions/ }))
    await waitFor(() => expect(genererPropositionsIA).toHaveBeenCalledWith(7))
    expect(await screen.findByText("3 propositions en cours de génération.")).toBeTruthy()
  })

  it("réponses sans proposition : le nombre est signalé (génération en cours ou modèle indisponible)", async () => {
    vi.mocked(listerCorrectionsIA).mockResolvedValue({ propositions: [], enCours: 2 })
    afficher()
    expect(await screen.findByText(/2 réponses sans proposition/)).toBeTruthy()
  })

  it("le texte de l'étudiant est affiché comme du texte, jamais comme du HTML", async () => {
    vi.mocked(listerCorrectionsIA).mockResolvedValue({ propositions: [proposition(1, { reponse: "<img src=x onerror=alert(1)>" })], enCours: 0 })
    afficher()
    await ouvrirLaPremiere()
    expect(await screen.findByText("<img src=x onerror=alert(1)>")).toBeTruthy()
    expect(document.querySelector("img")).toBeNull()
  })
})
