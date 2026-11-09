// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"
import type { QuestionExamen, ReponseItem, TypeQuestion } from "@/types"
import PassationEpreuve from "./PassationEpreuve"

/** Options passées à usePassation par l'écran, et langage reçu par l'éditeur de code. */
const etat = vi.hoisted(() => ({
  options: undefined as undefined | {
    depuisCopie: (q: unknown[]) => Record<number, unknown>
    versReponses: (r: Record<number, unknown>) => unknown[]
  },
  questions: [] as unknown[],
  reponses: {} as Record<number, unknown>,
}))

const question = vi.hoisted(() => (id: number, ordre: number, type: TypeQuestion, reponse: ReponseItem | null = null): QuestionExamen => ({
  id, enonce: `Énoncé ${ordre}`, ordre, points: 2, type, estChoixMultiple: false,
  choixPossibles: type === "QCM" ? [{ id: id * 10, texte: "Pile" }, { id: id * 10 + 1, texte: "File" }] : [],
  casDeTestsVisibles: type === "CODE" ? [{ id: 5, entree: "2 3", sortieAttendue: "5" }] : [],
  reponseActuelle: reponse,
}))

vi.mock("@/hooks/usePassation", () => ({
  usePassation: (options: typeof etat.options) => {
    etat.options = options
    return {
      copie: { id: 1, titreEvaluation: "Examen mixte", questions: etat.questions },
      questions: etat.questions, chargement: false, erreur: null, reponses: etat.reponses, setReponses: vi.fn(),
      tempsRestant: 1800, tempsRestantFormate: "30:00", derniereSauvegarde: null, erreurSauvegarde: false,
      soumettre: vi.fn(), soumissionEnCours: false, erreurSoumission: null, avertissement: null, changementsOnglet: 0,
    }
  },
}))
vi.mock("@monaco-editor/react", () => ({ default: () => <div>éditeur de code</div> }))

function afficher() {
  render(
    <MemoryRouter initialEntries={["/examen/7/epreuve"]}>
      <Routes>
        <Route path="/examen/:id/epreuve" element={<PassationEpreuve />} />
      </Routes>
    </MemoryRouter>
  )
}

describe("écran unique : épreuve mêlant QCM, question ouverte et code", () => {
  afterEach(() => {
    cleanup()
    etat.reponses = {}
  })

  it("chaque question s'affiche avec la saisie de son type", () => {
    etat.questions = [question(11, 1, "QCM"), question(12, 2, "OUVERTE"), question(13, 3, "CODE")]
    afficher()

    expect(screen.getByText("Pile")).toBeTruthy() // choix du QCM
    fireEvent.click(screen.getByRole("button", { name: /Suivant/ }))
    expect(screen.getByPlaceholderText(/Rédigez votre réponse/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Suivant/ }))
    expect(screen.getByText("éditeur de code")).toBeTruthy()
    expect(screen.getByText("2 3")).toBeTruthy() // cas de test visible
  })

  it("aucune question perdue : les réponses de chaque type partent au serveur dans leur format", () => {
    etat.questions = [question(11, 1, "QCM"), question(12, 2, "OUVERTE"), question(13, 3, "CODE")]
    afficher()
    const { depuisCopie, versReponses } = etat.options!

    const reponses = depuisCopie([
      question(11, 1, "QCM", { questionId: 11, choixIds: [110] }),
      question(12, 2, "OUVERTE", { questionId: 12, texteSoumis: "Une pile est LIFO." }),
      question(13, 3, "CODE", { questionId: 13, codeSoumis: "print(5)", langage: "python" }),
    ])

    expect(versReponses(reponses)).toEqual([
      { questionId: 11, choixIds: [110] },
      { questionId: 12, texteSoumis: "Une pile est LIFO." },
      { questionId: 13, codeSoumis: "print(5)", langage: "python" },
    ])
  })

  it("compteur et confirmation : questions traitées de tous types, mention des tests cachés s'il y a du code", () => {
    etat.questions = [question(11, 1, "QCM"), question(12, 2, "OUVERTE"), question(13, 3, "CODE")]
    etat.reponses = {
      11: { type: "choix", choixIds: [110] },
      12: { type: "texte", texte: "   " },
      13: { type: "code", code: "print(5)", langage: "python" },
    }
    afficher()

    expect(screen.getByText("2 / 3 traitées")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Terminer et soumettre/ }))
    expect(screen.getByRole("dialog").textContent).toContain("La question 2 est sans réponse")
    expect(screen.getByRole("dialog").textContent).toContain("cas de test")
  })
})
