// @vitest-environment jsdom
import type { ComponentType } from "react"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"
import type { QuestionExamen, TypeQuestion } from "@/types"
import PassationQCM from "./PassationQCM"
import PassationOuverte from "./PassationOuverte"
import PassationCode from "./PassationCode"

const etat = vi.hoisted(() => ({
  questions: [] as unknown[],
  reponses: {} as Record<number, unknown>,
  soumissionEnCours: false,
}))
vi.mock("@/hooks/usePassation", () => ({
  usePassation: () => ({
    copie: { id: 1, titreEvaluation: "Épreuve", questions: etat.questions },
    questions: etat.questions,
    chargement: false,
    erreur: null,
    reponses: etat.reponses,
    setReponses: vi.fn(),
    tempsRestant: 1800,
    tempsRestantFormate: "30:00",
    derniereSauvegarde: null,
    erreurSauvegarde: false,
    soumettre: vi.fn(),
    soumissionEnCours: etat.soumissionEnCours,
    erreurSoumission: null,
    avertissement: null,
    changementsOnglet: 0,
  }),
}))
// L'éditeur Monaco se charge depuis un CDN : remplacé par une zone de texte
vi.mock("@monaco-editor/react", () => ({ default: () => <textarea aria-label="éditeur" /> }))

function question(id: number, ordre: number, type: TypeQuestion): QuestionExamen {
  return {
    id,
    enonce: `Question ${ordre}`,
    ordre,
    points: 1,
    type,
    estChoixMultiple: false,
    choixPossibles: [{ id: id * 10, texte: "Choix" }],
    casDeTestsVisibles: [],
    reponseActuelle: null,
  }
}

function ouvrirConfirmation(Page: ComponentType) {
  render(
    <MemoryRouter initialEntries={["/examen/7"]}>
      <Routes>
        <Route path="/examen/:id" element={<Page />} />
      </Routes>
    </MemoryRouter>
  )
  // Depuis la première question : bouton « Terminer et soumettre », toujours accessible
  fireEvent.click(screen.getByRole("button", { name: /Terminer et soumettre/ }))
  return screen.getByRole("dialog")
}

// Écran, type de question, réponses où seules les questions 2 et 4 (sur 4) sont traitées
const cas: [string, ComponentType, TypeQuestion, Record<number, unknown>][] = [
  ["QCM", PassationQCM, "QCM", { 11: [], 12: [120], 13: [], 14: [140] }],
  ["questions ouvertes", PassationOuverte, "OUVERTE", { 11: "  ", 12: "réponse", 13: "", 14: "réponse" }],
  ["code", PassationCode, "CODE", { 11: { code: "", langage: "python" }, 12: { code: "print(1)", langage: "python" }, 13: { code: " ", langage: "python" }, 14: { code: "print(2)", langage: "python" } }],
]

describe("confirmation de soumission : questions sans réponse", () => {
  afterEach(() => {
    cleanup()
    etat.soumissionEnCours = false
  })

  it.each(cas)("%s : pendant l'envoi, la fenêtre ne se ferme pas et « Continuer » est désactivé", (_, Page, type) => {
    etat.questions = [11, 12].map((id, i) => question(id, i + 1, type))
    etat.reponses = {}
    etat.soumissionEnCours = true

    const fenetre = ouvrirConfirmation(Page)
    const continuer = screen.getByRole("button", { name: "Continuer" }) as HTMLButtonElement
    fireEvent.keyDown(fenetre, { key: "Escape" })

    expect(continuer.disabled).toBe(true)
    expect(screen.queryByRole("dialog")).not.toBeNull()
  })

  it.each(cas)("%s : la fenêtre liste les numéros des questions sans réponse", (_, Page, type, reponses) => {
    etat.questions = [11, 12, 13, 14].map((id, i) => question(id, i + 1, type))
    etat.reponses = reponses

    const fenetre = ouvrirConfirmation(Page)

    expect(fenetre.textContent).toContain(
      "Les questions 1 et 3 sont sans réponse et seront comptées comme non traitées."
    )
  })

  it.each(cas)("%s : rien n'est signalé quand tout est traité", (_, Page, type) => {
    etat.questions = [11, 12].map((id, i) => question(id, i + 1, type))
    etat.reponses =
      type === "QCM"
        ? { 11: [110], 12: [120] }
        : type === "CODE"
          ? { 11: { code: "a", langage: "python" }, 12: { code: "b", langage: "python" } }
          : { 11: "a", 12: "b" }

    const fenetre = ouvrirConfirmation(Page)

    expect(fenetre.textContent).not.toContain("sans réponse")
  })
})
