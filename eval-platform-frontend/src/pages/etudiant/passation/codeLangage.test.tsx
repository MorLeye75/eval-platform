// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"
import type { QuestionExamen, ReponseItem } from "@/types"
import PassationCode from "./PassationCode"

/** Options passées à usePassation par l'écran (conversion copie <-> réponses) et props reçues par l'éditeur. */
const etat = vi.hoisted(() => ({
  options: undefined as undefined | {
    depuisCopie: (q: unknown[]) => Record<number, unknown>
    versReponses: (r: Record<number, unknown>) => unknown[]
  },
  reponses: {} as Record<number, unknown>,
  langageEditeur: undefined as string | undefined,
}))

// Hissée avec vi.mock : utilisée par la simulation de usePassation, évaluée avant le reste du fichier
const QUESTION = vi.hoisted(() => (reponse: ReponseItem | null): QuestionExamen => ({
  id: 11, enonce: "Afficher la somme.", ordre: 1, points: 4, type: "CODE", estChoixMultiple: null,
  choixPossibles: [], casDeTestsVisibles: [{ id: 5, entree: "2 3", sortieAttendue: "5" }], reponseActuelle: reponse,
}))

vi.mock("@/hooks/usePassation", () => ({
  usePassation: (options: typeof etat.options) => {
    etat.options = options
    return {
      copie: { id: 1, titreEvaluation: "Algorithmique", questions: [QUESTION(null)] },
      questions: [QUESTION(null)], chargement: false, erreur: null, reponses: etat.reponses, setReponses: vi.fn(),
      tempsRestant: 1800, tempsRestantFormate: "30:00", derniereSauvegarde: null, erreurSauvegarde: false,
      soumettre: vi.fn(), soumissionEnCours: false, erreurSoumission: null, avertissement: null, changementsOnglet: 0,
    }
  },
}))
vi.mock("@monaco-editor/react", () => ({
  default: (props: { language: string }) => {
    etat.langageEditeur = props.language
    return <div>éditeur</div>
  },
}))

function afficher() {
  render(
    <MemoryRouter initialEntries={["/examen/7/code"]}>
      <Routes>
        <Route path="/examen/:id/code" element={<PassationCode />} />
      </Routes>
    </MemoryRouter>
  )
}

describe("écran de code : langage enregistré, pas d'exécution simulée", () => {
  afterEach(() => {
    cleanup()
    etat.reponses = {}
  })

  it("le langage fait partie de la réponse : restitué depuis la copie et envoyé à la sauvegarde", () => {
    afficher()
    const { depuisCopie, versReponses } = etat.options!

    const reponses = depuisCopie([QUESTION({ questionId: 11, codeSoumis: "print(5)", langage: "python" })])
    expect(versReponses(reponses)).toEqual([{ questionId: 11, codeSoumis: "print(5)", langage: "python" }])
  })

  it("l'éditeur utilise le langage enregistré", () => {
    etat.reponses = { 11: { code: "System.out.println(5);", langage: "java" } }
    afficher()

    expect(etat.langageEditeur).toBe("java")
  })

  it("pas de faux « Réussi » : l'exécution est annoncée pour la correction, le bouton est désactivé", () => {
    etat.reponses = { 11: { code: "print(5)", langage: "python" } }
    afficher()

    const bouton = screen.getByRole("button", { name: /Exécution disponible à la correction/ }) as HTMLButtonElement
    expect(bouton.disabled).toBe(true)
    fireEvent.click(bouton)
    expect(screen.queryByText("Réussi")).toBeNull()
    expect(screen.getByText("2 3")).toBeTruthy() // cas de test visibles toujours affichés
  })
})
