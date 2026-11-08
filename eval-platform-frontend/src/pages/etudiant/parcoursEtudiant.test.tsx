// @vitest-environment jsdom
import type { ReactNode } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { api } from "@/lib/axios"
import type { EvaluationEtudiant } from "@/types"
import ListeEvaluations from "./ListeEvaluations"
import DetailEvaluation from "./DetailEvaluation"

const navigate = vi.fn()
vi.mock("react-router-dom", async (original) => ({
  ...(await original<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}))

function evaluation(e: Partial<EvaluationEtudiant>): EvaluationEtudiant {
  return {
    id: 7, titre: "Structures de données — QCM", description: "QCM sur les piles et files.", statut: "EN_COURS",
    dateOuverture: "2026-11-07T08:00:00", dateFermeture: "2026-11-07T18:00:00",
    dureeMinutes: 40, dureeQuestion: null, nbQuestions: 5, totalPoints: 10, typesQuestions: ["QCM"],
    resultatsPublies: false, soumissionId: null, statutSoumission: null, noteFinale: null, ...e,
  }
}

const EVALUATIONS = [
  evaluation({}),
  evaluation({ id: 8, titre: "Réseaux — mixte", typesQuestions: ["QCM", "OUVERTE"] }),
  evaluation({
    id: 9, titre: "Algorithmique", statut: "TERMINEE", resultatsPublies: true,
    soumissionId: 90, statutSoumission: "EVALUE", noteFinale: 8.5,
  }),
  evaluation({ id: 10, titre: "Bases de données", dateOuverture: "2026-11-20T08:00:00", dateFermeture: "2026-11-20T18:00:00" }),
]

function afficher(element: ReactNode, chemin: string, motif: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[chemin]}>
        <Routes>
          <Route path={motif} element={element} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("parcours étudiant branché sur GET /etudiant/evaluations", () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date("2026-11-07T10:00:00"), toFake: ["Date"] })
    vi.spyOn(api, "get").mockResolvedValue({ data: EVALUATIONS })
    navigate.mockReset()
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it("liste : évaluations du serveur avec leur état, durée (tiers-temps compris) et note publiée", async () => {
    afficher(<ListeEvaluations />, "/etudiant/evaluations", "/etudiant/evaluations")

    expect(await screen.findByText("Structures de données — QCM")).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith("/etudiant/evaluations")
    expect(screen.getAllByText("Disponible").length).toBe(2)
    expect(screen.getAllByText("À venir").length).toBe(2) // onglet de filtre + badge de l'évaluation
    expect(screen.getByText("Terminée")).toBeTruthy()
    expect(screen.getAllByText("40 min").length).toBe(4)
    expect(screen.getByText("Note : 8.5 / 10")).toBeTruthy()
    expect(screen.getByText("Épreuve mixte")).toBeTruthy()
  })

  it("détail : barème du serveur, et démarrage vers l'écran du type d'épreuve", async () => {
    afficher(<DetailEvaluation />, "/etudiant/evaluations/7", "/etudiant/evaluations/:id")

    expect(await screen.findByText("Structures de données — QCM")).toBeTruthy()
    expect(screen.getByText("10 pts")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Démarrer l'évaluation/ }))
    expect(navigate).toHaveBeenCalledWith("/examen/7/epreuve")
  })

  it("détail d'une épreuve mixte : démarrage sur l'écran unique", async () => {
    afficher(<DetailEvaluation />, "/etudiant/evaluations/8", "/etudiant/evaluations/:id")

    expect(await screen.findByText("Réseaux — mixte")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Démarrer l'évaluation/ }))
    expect(navigate).toHaveBeenCalledWith("/examen/8/epreuve")
  })

  it("évaluation inconnue de l'étudiant : message d'absence", async () => {
    afficher(<DetailEvaluation />, "/etudiant/evaluations/999", "/etudiant/evaluations/:id")

    expect(await screen.findByText("Cette évaluation est introuvable.")).toBeTruthy()
  })
})

describe("aucune donnée simulée dans le parcours étudiant (les mocks contiennent les bonnes réponses)", () => {
  const sources = import.meta.glob(["./ListeEvaluations.tsx", "./DetailEvaluation.tsx"], {
    query: "?raw", import: "default", eager: true,
  }) as Record<string, string>

  it.each(Object.keys(sources))("%s n'importe pas @/mocks/evaluations", (fichier) => {
    expect(sources[fichier]).not.toContain("@/mocks/evaluations")
  })
})
