// @vitest-environment jsdom
import type { ComponentType } from "react"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { QuestionExamen, TypeQuestion } from "@/types"
import PassationQCM from "./PassationQCM"
import PassationOuverte from "./PassationOuverte"
import PassationCode from "./PassationCode"

/**
 * Caractérisation des parties communes aux trois écrans de passation (en-tête, bandeaux, navigation) :
 * ce comportement doit rester identique après leur regroupement dans un composant partagé.
 */
const etat = vi.hoisted(() => ({
  questions: [] as unknown[],
  reponses: {} as Record<number, unknown>,
  derniereSauvegarde: null as string | null,
  erreurSauvegarde: false,
  avertissement: null as string | null,
  changementsOnglet: 0,
  tempsRestant: 1800,
}))
vi.mock("@/hooks/usePassation", () => ({
  usePassation: () => ({
    copie: { id: 1, titreEvaluation: "Structures de données", questions: etat.questions },
    questions: etat.questions,
    chargement: false,
    erreur: null,
    reponses: etat.reponses,
    setReponses: vi.fn(),
    tempsRestant: etat.tempsRestant,
    tempsRestantFormate: "29:59",
    derniereSauvegarde: etat.derniereSauvegarde,
    erreurSauvegarde: etat.erreurSauvegarde,
    soumettre: vi.fn(),
    soumissionEnCours: false,
    erreurSoumission: null,
    avertissement: etat.avertissement,
    changementsOnglet: etat.changementsOnglet,
  }),
}))
vi.mock("@monaco-editor/react", () => ({ default: () => <div>éditeur</div> }))

function question(id: number, ordre: number, type: TypeQuestion): QuestionExamen {
  return {
    id, enonce: `Énoncé ${ordre}`, ordre, points: ordre + 1, type, estChoixMultiple: false,
    choixPossibles: [{ id: id * 10, texte: `Choix ${ordre}` }], casDeTestsVisibles: [], reponseActuelle: null,
  }
}

function afficher(Page: ComponentType) {
  render(
    <MemoryRouter initialEntries={["/examen/7"]}>
      <Routes>
        <Route path="/examen/:id" element={<Page />} />
      </Routes>
    </MemoryRouter>
  )
}

// Écran, type, réponses avec la 1re question traitée sur 2, libellé du compteur
const cas: [string, ComponentType, TypeQuestion, Record<number, unknown>, string][] = [
  ["QCM", PassationQCM, "QCM", { 11: [110], 12: [] }, "1 / 2 répondues"],
  ["questions ouvertes", PassationOuverte, "OUVERTE", { 11: "réponse", 12: "" }, "1 / 2 traitées"],
  ["code", PassationCode, "CODE", { 11: { code: "print(1)", langage: "python" }, 12: { code: "", langage: "python" } }, "1 / 2 traitées"],
]

describe.each(cas)("écran de passation %s : parties communes", (_, Page, type, reponses, compteur) => {
  beforeEach(() => {
    etat.questions = [question(11, 1, type), question(12, 2, type)]
    etat.reponses = reponses
    etat.derniereSauvegarde = null
    etat.erreurSauvegarde = false
    etat.avertissement = null
    etat.changementsOnglet = 0
  })

  afterEach(() => {
    cleanup()
  })

  it("en-tête : titre, chrono, compteur et état de la sauvegarde", () => {
    etat.derniereSauvegarde = "10:42"
    afficher(Page)

    expect(screen.getByText("Structures de données")).toBeTruthy()
    expect(screen.getByText("29:59")).toBeTruthy()
    expect(screen.getByText(compteur)).toBeTruthy()
    expect(screen.getByText("Sauvegardé à 10:42")).toBeTruthy()
  })

  it("sauvegarde en échec : les réponses sont annoncées comme conservées sur l'appareil", () => {
    etat.erreurSauvegarde = true
    afficher(Page)

    expect(screen.getByText("Hors ligne : réponses conservées sur cet appareil")).toBeTruthy()
  })

  it("bandeaux : copie rechargée et sorties de la page", () => {
    etat.avertissement = "Votre copie a été modifiée depuis un autre onglet."
    etat.changementsOnglet = 2
    afficher(Page)

    expect(screen.getByText("Votre copie a été modifiée depuis un autre onglet.")).toBeTruthy()
    expect(screen.getByText(/2 sorties de la\s+page détectées et enregistrées/)).toBeTruthy()
  })

  it("navigation : précédent, suivant, numéros de question et bouton de soumission en fin d'épreuve", () => {
    afficher(Page)
    expect(screen.getByText("Question 1 sur 2")).toBeTruthy()
    expect(screen.getByText("2 pts")).toBeTruthy()
    expect((screen.getByRole("button", { name: /Précédent/ }) as HTMLButtonElement).disabled).toBe(true)

    fireEvent.click(screen.getByRole("button", { name: /Suivant/ }))
    expect(screen.getByText("Question 2 sur 2")).toBeTruthy()
    expect(screen.getByText("3 pts")).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Terminer et soumettre/ })).toBeNull()
    expect(screen.getByRole("button", { name: /^Soumettre$/ })).toBeTruthy()

    fireEvent.click(screen.getByRole("button", { name: "1" }))
    expect(screen.getByText("Question 1 sur 2")).toBeTruthy()
  })
})
