// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { CLE_BROUILLON_EVALUATION } from "@/lib/session"
import BuilderQuestions from "./BuilderQuestions"

describe("constructeur de questions : champs nommés pour les lecteurs d'écran", () => {
  beforeEach(() => { try { sessionStorage.clear(); localStorage.clear() } catch { /* sans stockage */ } })
  afterEach(() => cleanup())

  it("une question QCM expose son énoncé, ses points et chacun de ses choix par un nom accessible", () => {
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify({
      evaluationId: null,
      infos: { titre: "Structures de données", description: "QCM", duree: 60, dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T18:00", statut: "BROUILLON", promotionIds: [1] },
      questions: [],
    }))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><MemoryRouter><BuilderQuestions /></MemoryRouter></QueryClientProvider>)
    fireEvent.click(screen.getByRole("button", { name: /Ajouter une question/ }))

    expect(screen.getByLabelText("Énoncé de la question 1")).toBeTruthy()
    expect(screen.getByLabelText("Points de la question 1")).toBeTruthy()
    expect(screen.getByLabelText("Choix multiples, question 1")).toBeTruthy()
    expect(screen.getByLabelText("Texte du choix 1, question 1")).toBeTruthy()
    expect(screen.getByLabelText("Bonne réponse : choix 2, question 1")).toBeTruthy()
    expect(screen.getByRole("button", { name: "Supprimer le choix 2, question 1" })).toBeTruthy()
  })

  it("barème négatif : un QCM a un champ « pénalité » (0 par défaut), une question ouverte n'en a pas", () => {
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify({
      evaluationId: null,
      infos: { titre: "Structures de données", description: "QCM", duree: 60, dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T18:00", statut: "BROUILLON", promotionIds: [1] },
      questions: [
        { localId: 1, type: "QCM", enonce: "Pile ?", points: 2, estChoixMultiple: false,
          choix: [{ id: 1, texte: "LIFO", estCorrect: true }, { id: 2, texte: "FIFO", estCorrect: false }] },
        { localId: 2, type: "OUVERTE", enonce: "Expliquez", points: 4 },
      ],
    }))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><MemoryRouter><BuilderQuestions /></MemoryRouter></QueryClientProvider>)
    const ouvrir = (n: number) => fireEvent.click(screen.getByText(n === 1 ? "Pile ?" : "Expliquez"))
    if (!screen.queryByLabelText("Énoncé de la question 1")) ouvrir(1)

    const penalite = screen.getByLabelText("Pénalité si mauvaise réponse, question 1") as HTMLInputElement
    expect(penalite.value).toBe("0")
    fireEvent.change(penalite, { target: { value: "0.5" } })
    expect((screen.getByLabelText("Pénalité si mauvaise réponse, question 1") as HTMLInputElement).value).toBe("0.5")

    ouvrir(2)
    expect(screen.getByLabelText("Points de la question 2")).toBeTruthy()
    expect(screen.queryByLabelText("Pénalité si mauvaise réponse, question 2")).toBeNull()
  })
})
