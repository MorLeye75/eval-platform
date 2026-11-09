// @vitest-environment jsdom
import type { ComponentType } from "react"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"
import type { QuestionExamen, TypeQuestion } from "@/types"
import PassationEpreuve from "./PassationEpreuve"

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
  ["QCM", PassationEpreuve, "QCM", { 11: { type: "choix", choixIds: [] }, 12: { type: "choix", choixIds: [120] }, 13: { type: "choix", choixIds: [] }, 14: { type: "choix", choixIds: [140] } }],
  ["questions ouvertes", PassationEpreuve, "OUVERTE", { 11: { type: "texte", texte: "  " }, 12: { type: "texte", texte: "réponse" }, 13: { type: "texte", texte: "" }, 14: { type: "texte", texte: "réponse" } }],
  ["code", PassationEpreuve, "CODE", { 11: { type: "code", code: "", langage: "python" }, 12: { type: "code", code: "print(1)", langage: "python" }, 13: { type: "code", code: " ", langage: "python" }, 14: { type: "code", code: "print(2)", langage: "python" } }],
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
        ? { 11: { type: "choix", choixIds: [110] }, 12: { type: "choix", choixIds: [120] } }
        : type === "CODE"
          ? { 11: { type: "code", code: "a", langage: "python" }, 12: { type: "code", code: "b", langage: "python" } }
          : { 11: { type: "texte", texte: "a" }, 12: { type: "texte", texte: "b" } }

    const fenetre = ouvrirConfirmation(Page)

    expect(fenetre.textContent).not.toContain("sans réponse")
  })
})
