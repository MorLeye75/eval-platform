// @vitest-environment jsdom
import type { ComponentType } from "react"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest"
import type { QuestionExamen, TypeQuestion } from "@/types"
import { TAILLE_MAX_CODE, TAILLE_MAX_TEXTE } from "@/lib/passation"
import PassationEpreuve from "./PassationEpreuve"

type Reponses = Record<number, string>
const etat = vi.hoisted(() => ({
  questions: [] as unknown[],
  reponses: {} as Record<number, string>,
  setReponses: undefined as unknown as ReturnType<typeof vi.fn>,
  editeur: undefined as undefined | ((valeur: string) => void),
}))
vi.mock("@/hooks/usePassation", () => ({
  usePassation: () => ({
    copie: { id: 1, titreEvaluation: "Épreuve", questions: etat.questions },
    questions: etat.questions,
    chargement: false,
    erreur: null,
    reponses: etat.reponses,
    setReponses: etat.setReponses,
    tempsRestant: 1800,
    tempsRestantFormate: "30:00",
    derniereSauvegarde: null,
    erreurSauvegarde: false,
    soumettre: vi.fn(),
    soumissionEnCours: false,
    erreurSoumission: null,
    avertissement: null,
    changementsOnglet: 0,
  }),
}))
vi.mock("@monaco-editor/react", () => ({
  default: (props: { onChange: (v: string) => void }) => {
    etat.editeur = props.onChange
    return <div>éditeur</div>
  },
}))

function question(type: TypeQuestion): QuestionExamen {
  return {
    id: 11, enonce: "Énoncé", ordre: 1, points: 1, type, estChoixMultiple: false,
    choixPossibles: [], casDeTestsVisibles: [], reponseActuelle: null,
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

/** Applique la dernière mise à jour passée à setReponses (fonction ou valeur) sur des réponses vides. */
function derniereMiseAJour(): Reponses {
  const arg = etat.setReponses.mock.calls.at(-1)?.[0]
  return typeof arg === "function" ? arg({}) : arg
}


// Saisie de code chargée à la demande (React.lazy) : préchargée pour ne pas dépendre de la charge de la machine
beforeAll(async () => {
  await import("./SaisieCode")
})

describe("limites de taille des réponses (alignées sur le serveur)", () => {
  beforeEach(() => {
    etat.setReponses = vi.fn()
    etat.reponses = {}
  })

  afterEach(() => {
    cleanup()
  })

  it("le serveur refuse au-delà de 20 000 / 50 000 caractères : mêmes limites côté écran", () => {
    expect(TAILLE_MAX_TEXTE).toBe(20_000)
    expect(TAILLE_MAX_CODE).toBe(50_000)
  })

  it("question ouverte : saisie bornée à 20 000 caractères", () => {
    etat.questions = [question("OUVERTE")]
    afficher(PassationEpreuve)
    const zone = screen.getByPlaceholderText(/Rédigez votre réponse/) as HTMLTextAreaElement

    expect(zone.maxLength).toBe(TAILLE_MAX_TEXTE)
    fireEvent.change(zone, { target: { value: "a".repeat(TAILLE_MAX_TEXTE + 500) } })
    expect((derniereMiseAJour()[11] as unknown as { texte: string }).texte).toHaveLength(TAILLE_MAX_TEXTE)
  })

  it("question ouverte : la limite atteinte est signalée", () => {
    etat.questions = [question("OUVERTE")]
    etat.reponses = { 11: { type: "texte", texte: "a".repeat(TAILLE_MAX_TEXTE) } } as unknown as Record<number, string>
    afficher(PassationEpreuve)

    expect(screen.getByText(/Limite de 20 000 caractères atteinte/)).toBeTruthy()
  })

  it("code : au-delà de 50 000 caractères, le code est coupé et la limite signalée", async () => {
    etat.questions = [question("CODE")]
    afficher(PassationEpreuve)

    await screen.findByText("éditeur") // saisie de code chargée à la demande
    etat.editeur!("x".repeat(TAILLE_MAX_CODE + 10))
    expect((derniereMiseAJour()[11] as unknown as { code: string }).code).toHaveLength(TAILLE_MAX_CODE)

    cleanup()
    etat.reponses = { 11: { type: "code", code: "x".repeat(TAILLE_MAX_CODE), langage: "python" } } as unknown as Record<number, string>
    afficher(PassationEpreuve)
    expect(await screen.findByText(/Limite de 50 000 caractères atteinte/)).toBeTruthy()
  })
})
