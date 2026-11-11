// @vitest-environment jsdom
import type { ReactNode } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { demarrerEpreuve, questionQuizSuivante, sauvegarderReponses, soumettreCopie } from "@/lib/passation"
import type { CopieEnCours, QuestionExamen } from "@/types"
import PassationQuiz from "./PassationQuiz"
import ExpirationScreen from "./ExpirationScreen"

const navigate = vi.fn()
vi.mock("react-router-dom", async (original) => ({
  ...(await original<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}))
vi.mock("@/lib/passation", async (original) => ({
  ...(await original<typeof import("@/lib/passation")>()),
  demarrerEpreuve: vi.fn(),
  sauvegarderReponses: vi.fn().mockResolvedValue({ dateSauvegarde: "2026-11-07T10:00:00", versionReponses: 1 }),
  soumettreCopie: vi.fn().mockResolvedValue("2026-11-07T10:05:00"),
  questionQuizSuivante: vi.fn(),
  signalerChangementOnglet: vi.fn().mockResolvedValue(1),
}))

function question(id: number, ordre: number): QuestionExamen {
  return {
    id, enonce: `Question numéro ${ordre}`, ordre, points: 1, type: "QUIZ_CHRONOMETREE", estChoixMultiple: false,
    choixPossibles: [{ id: id * 10, texte: `Choix ${ordre}` }], casDeTestsVisibles: [], reponseActuelle: null,
    dureeQuestion: 30,
  }
}

/** Copie d'un quiz de 2 questions, sur la question `index` (seule envoyée par le serveur). */
function copieQuiz(index: number, tempsRestantQuestion = 30): CopieEnCours {
  return {
    id: 12, evaluationId: 7, titreEvaluation: "Quiz Réseaux", descriptionEvaluation: null, statut: "EN_COURS",
    dateDebut: "2026-11-07T10:00:00", dateRemise: null, versionReponses: 0, dureeMinutes: 10,
    tempsRestantSecondes: 600, nbChangementsOnglet: 0,
    questions: index < 2 ? [question(11 + index, index + 1)] : [],
    quiz: { indexQuestion: index, nbQuestions: 2, dureeQuestion: index < 2 ? 30 : null, tempsRestantQuestionSecondes: tempsRestantQuestion },
  }
}

function afficher() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = (enfant: ReactNode) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/examen/7/quiz"]}>
        <Routes>
          <Route path="/examen/:id/quiz" element={enfant} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
  render(wrapper(<PassationQuiz />))
}

describe("quiz chronométré branché sur le serveur", () => {
  beforeEach(() => {
    localStorage.clear()
    navigate.mockReset()
    vi.mocked(questionQuizSuivante).mockReset()
    vi.mocked(soumettreCopie).mockClear()
  })

  afterEach(() => {
    cleanup()
  })

  it("affiche la seule question envoyée par le serveur, avec son chrono et la position dans le quiz", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue(copieQuiz(0))
    afficher()

    expect(await screen.findByText("Question numéro 1")).toBeTruthy()
    expect(screen.getByText("Question 1 / 2")).toBeTruthy()
    expect(screen.getByText(/^(30|29)s$/)).toBeTruthy() // une seconde a pu s'écouler pendant le rendu
    expect(demarrerEpreuve).toHaveBeenCalledWith("7")
  })

  it("« Question suivante » : le serveur fait avancer la copie, la question suivante s'affiche", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue(copieQuiz(0))
    vi.mocked(questionQuizSuivante).mockResolvedValue(copieQuiz(1))
    afficher()
    await screen.findByText("Question numéro 1")

    fireEvent.click(screen.getByRole("button", { name: /Question suivante/ }))

    expect(await screen.findByText("Question numéro 2")).toBeTruthy()
    expect(questionQuizSuivante).toHaveBeenCalledWith(12, 0)
    expect(screen.getByRole("button", { name: /Terminer le quiz/ })).toBeTruthy()
  })

  it("réponse non enregistrée (réseau) : on reste sur la question, avec un message, sans la perdre", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue(copieQuiz(0))
    vi.mocked(questionQuizSuivante).mockResolvedValue(copieQuiz(1))
    vi.mocked(sauvegarderReponses).mockRejectedValueOnce(new Error("Network Error"))
    afficher()

    fireEvent.click(await screen.findByText("Choix 1"))
    fireEvent.click(screen.getByRole("button", { name: /Question suivante/ }))

    expect(await screen.findByText(/Votre réponse n'a pas pu être enregistrée/)).toBeTruthy()
    expect(questionQuizSuivante).not.toHaveBeenCalled() // le serveur n'avance pas : la réponse reste modifiable
    expect(screen.getByText("Question numéro 1")).toBeTruthy()

    // Le réseau revient : nouvel essai, la réponse part puis la copie avance
    fireEvent.click(screen.getByRole("button", { name: /Question suivante/ }))
    expect(await screen.findByText("Question numéro 2")).toBeTruthy()
    expect(sauvegarderReponses).toHaveBeenLastCalledWith(12,
      [expect.objectContaining({ questionId: 11, choixIds: [110] })], expect.anything())
  })

  it("dernière question : « Terminer le quiz » remet la copie", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue(copieQuiz(1))
    afficher()
    await screen.findByText("Question numéro 2")

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: /Terminer le quiz/ }))
    })

    await waitFor(() => expect(soumettreCopie).toHaveBeenCalledWith(12))
    expect(navigate).toHaveBeenCalledWith("/examen/7/confirmation", expect.anything())
  })

  it("temps de la question écoulé : passage automatique à la suivante", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue(copieQuiz(0, 1))
    vi.mocked(questionQuizSuivante).mockResolvedValue(copieQuiz(1))
    afficher()
    await screen.findByText("Question numéro 1")

    await waitFor(() => expect(questionQuizSuivante).toHaveBeenCalledWith(12, 0), { timeout: 3000 })
    expect(await screen.findByText("Question numéro 2")).toBeTruthy()
  })

  it("quiz déjà terminé à la reprise (toutes les questions closes) : la copie est remise", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue(copieQuiz(2, 0))
    afficher()

    await waitFor(() => expect(soumettreCopie).toHaveBeenCalledWith(12))
  })

  it("quiz terminé et remise en échec (réseau) : message et bouton pour réessayer, pas d'attente sans fin", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue(copieQuiz(2, 0))
    vi.mocked(soumettreCopie).mockRejectedValueOnce(new Error("Network Error"))
    afficher()

    expect(await screen.findByText(/La soumission a échoué/)).toBeTruthy()
    expect(navigate).not.toHaveBeenCalled()

    // Le réseau revient : nouvel essai, la copie est remise
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: /Réessayer la remise/ }))
    })
    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/examen/7/confirmation", expect.anything()))
    expect(soumettreCopie).toHaveBeenCalledTimes(2)
  })
})

describe("aucune donnée simulée dans le quiz (les mocks contiennent les bonnes réponses)", () => {
  const sources = import.meta.glob(["./PassationQuiz.tsx", "./ExpirationScreen.tsx"], {
    query: "?raw", import: "default", eager: true,
  }) as Record<string, string>

  it.each(Object.keys(sources))("%s n'importe pas @/mocks/evaluations", (fichier) => {
    expect(sources[fichier]).not.toContain("@/mocks/evaluations")
  })

  it("écran d'expiration : titre et questions traitées transmis par l'écran de passation", () => {
    render(
      <MemoryRouter initialEntries={[{ pathname: "/examen/7/expiration", state: { titre: "Quiz Réseaux", traitees: 2, total: 3 } }]}>
        <Routes>
          <Route path="/examen/:id/expiration" element={<ExpirationScreen />} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText("Quiz Réseaux")).toBeTruthy()
    expect(screen.getByText("2 / 3")).toBeTruthy()
    cleanup()
  })
})
