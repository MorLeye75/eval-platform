// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { listerCorrectionsIA, type PropositionIAApi } from "@/lib/correctionsIA"
import { mesEvaluationsEnseignant, type EvaluationEnseignantApi } from "@/lib/evaluationsEnseignant"
import DashboardEnseignant from "./DashboardEnseignant"

vi.mock("@/context/AuthContext", () => ({ useAuth: () => ({ user: { prenom: "Mamadou", role: "ENSEIGNANT" } }) }))
vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
}))
vi.mock("@/lib/correctionsIA", async (original) => ({
  ...(await original<typeof import("@/lib/correctionsIA")>()),
  listerCorrectionsIA: vi.fn(),
}))

const evaluation = (id: number, statut: EvaluationEnseignantApi["statut"], resultatsPublies = false): EvaluationEnseignantApi => ({
  id, titre: `Évaluation ${id}`, description: null, dureeStandard: 60, dateOuverture: "2030-01-10T08:00:00",
  dateFermeture: "2030-01-10T18:00:00", statut, resultatsPublies, version: 0, promotionIds: [1],
  nbQuestions: 3, nbCopies: 10, nbAttendus: 45, questions: [], amenagements: [],
})

const proposition = (id: number, statut: PropositionIAApi["statut"]): PropositionIAApi => ({
  id, reponseId: id, evaluationId: 1, evaluation: "E", etudiant: `Étudiant ${id}`, question: `Question ${id}`, reponse: "r",
  pointsMax: 5, noteProposee: 4, justification: null, statut, noteRetenue: null,
})

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter><DashboardEnseignant /></MemoryRouter></QueryClientProvider>)
}

/** Le chiffre affiché dans la carte portant ce libellé. */
function carte(libelle: string) {
  const titre = screen.getByText(libelle)
  return within(titre.closest("[data-slot=card]") as HTMLElement)
}

describe("tableau de bord de l'enseignant", () => {
  beforeEach(() => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([
      evaluation(1, "EN_COURS"), evaluation(2, "TERMINEE"), evaluation(3, "TERMINEE", true), evaluation(4, "BROUILLON"),
      evaluation(5, "BROUILLON"), evaluation(6, "PUBLIEE"),
    ])
    vi.mocked(listerCorrectionsIA).mockResolvedValue({
      propositions: [proposition(1, "EN_ATTENTE"), proposition(2, "VALIDEE"), proposition(3, "EN_ATTENTE")], enCours: 0,
    })
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("les chiffres viennent des évaluations et des corrections de l'API", async () => {
    afficher()
    await screen.findByText("Étudiant 1")

    expect(carte("Évaluations en cours").getByText("1")).toBeTruthy()
    expect(carte("Résultats à publier").getByText("1")).toBeTruthy()   // seulement la terminée dont les résultats ne sont pas publiés
    expect(carte("Corrections IA en attente").getByText("2")).toBeTruthy()  // la validée n'est pas comptée
    expect(carte("Brouillons").getByText("2")).toBeTruthy()
    expect(screen.getByText("Bonjour, Mamadou", { exact: false })).toBeTruthy()
  })

  it("liste les 4 évaluations les plus récentes avec leurs copies, et les corrections IA à valider", async () => {
    afficher()
    expect(await screen.findByText("Évaluation 1")).toBeTruthy()
    expect(screen.getByText("Évaluation 4")).toBeTruthy()
    expect(screen.queryByText("Évaluation 5")).toBeNull()
    expect(screen.getAllByText("10 / 45 copies").length).toBe(4)
    expect(screen.getByText("Question 3")).toBeTruthy()
    expect(screen.queryByText("Question 2")).toBeNull()
  })

  it("pendant le chargement : « — », jamais un 0 trompeur", () => {
    vi.mocked(mesEvaluationsEnseignant).mockReturnValue(new Promise(() => {}))
    afficher()
    expect(carte("Évaluations en cours").getByText("—")).toBeTruthy()
  })

  it("sans donnée : messages vides clairs", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([])
    vi.mocked(listerCorrectionsIA).mockResolvedValue({ propositions: [], enCours: 0 })
    afficher()
    expect(await screen.findByText("Aucune évaluation pour l'instant.")).toBeTruthy()
    expect(screen.getByText("Aucune correction en attente.")).toBeTruthy()
  })

  it("une source en erreur : message affiché", async () => {
    vi.mocked(listerCorrectionsIA).mockRejectedValue(new Error("réseau"))
    afficher()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
