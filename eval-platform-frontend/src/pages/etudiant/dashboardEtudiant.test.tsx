// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { api } from "@/lib/axios"
import type { EvaluationEtudiant, Performances } from "@/types"
import DashboardEtudiant from "./DashboardEtudiant"

vi.mock("@/context/AuthContext", () => ({ useAuth: () => ({ user: { prenom: "Aminata" } }) }))

function evaluation(e: Partial<EvaluationEtudiant>): EvaluationEtudiant {
  return {
    id: 7, titre: "Structures de données", description: null, statut: "EN_COURS",
    dateOuverture: "2026-11-07T08:00:00", dateFermeture: "2026-11-07T18:00:00",
    dureeMinutes: 40, dureeQuestion: null, nbQuestions: 5, totalPoints: 10, typesQuestions: ["QCM"],
    resultatsPublies: false, soumissionId: null, statutSoumission: null, noteFinale: null, ...e,
  }
}

// Ordre du serveur : par date d'ouverture, la plus récente d'abord
const EVALUATIONS = [
  evaluation({ id: 10, titre: "Bases de données", statut: "PUBLIEE",
    dateOuverture: "2026-11-20T08:00:00", dateFermeture: "2026-11-20T18:00:00" }),
  evaluation({ id: 9, titre: "Algorithmique", statut: "TERMINEE", resultatsPublies: true,
    soumissionId: 90, statutSoumission: "EVALUE", noteFinale: 8.5 }),
  evaluation({ id: 8, titre: "Réseaux", statut: "TERMINEE", soumissionId: 80, statutSoumission: "SOUMIS" }),
  evaluation({ id: 7, titre: "Structures de données", soumissionId: 70, statutSoumission: "EN_COURS" }),
]

const PERFORMANCES: Performances = {
  historique: [
    { soumissionId: 90, evaluationId: 9, titre: "Algorithmique", date: "2026-11-01T10:00:00", noteSur20: 17, moyennePromotionSur20: 12 },
    { soumissionId: 60, evaluationId: 6, titre: "Systèmes", date: "2026-10-01T10:00:00", noteSur20: 8, moyennePromotionSur20: null },
  ],
  moyenneGenerale: 12.5, moyennePromotionGenerale: 12, ecartMoyenne: 5, nbEvaluations: 2,
}

function repondre(evaluations: EvaluationEtudiant[], performances: Performances) {
  vi.spyOn(api, "get").mockImplementation(async (url: string) => {
    if (url === "/etudiant/evaluations") return { data: evaluations }
    if (url === "/etudiant/performances") return { data: performances }
    throw new Error(`URL inattendue : ${url}`)
  })
}

function afficher() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardEtudiant />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("accueil étudiant branché sur l'API", () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date("2026-11-07T10:00:00"), toFake: ["Date"] })
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it("évaluations : disponibles d'abord, puis à venir, puis terminées, chacune vers le bon écran", async () => {
    repondre(EVALUATIONS, PERFORMANCES)
    afficher()

    expect(await screen.findByText("Copie en cours")).toBeTruthy()
    const titres = screen.getAllByRole("link").map((l) => l.textContent ?? "")
    const ordre = ["Structures de données", "Bases de données", "Algorithmique", "Réseaux"]
      .map((t) => titres.findIndex((x) => x.includes(t)))
    expect(ordre).toEqual([...ordre].sort((a, b) => a - b))

    expect(screen.getByRole("link", { name: /Algorithmique/ }).getAttribute("href")).toBe("/etudiant/resultats/90")
    expect(screen.getByRole("link", { name: /Réseaux/ }).getAttribute("href")).toBe("/etudiant/evaluations/8")
    expect(screen.getByRole("link", { name: /Structures de données/ }).getAttribute("href")).toBe("/etudiant/evaluations/7")
    expect(screen.getByText("Note : 8,5 / 10")).toBeTruthy()
    expect(screen.getByText("Copie remise · en attente des résultats")).toBeTruthy()
    expect(screen.getByText(/^Ouvre le/)).toBeTruthy()
    expect(screen.getByText("À venir")).toBeTruthy()
    expect(screen.getByText("Résultats publiés")).toBeTruthy()
  })

  it("indicateurs : disponibles maintenant, moyenne et taux de réussite des résultats publiés", async () => {
    repondre(EVALUATIONS, PERFORMANCES)
    afficher()

    expect(await screen.findByText("12,5 / 20")).toBeTruthy()
    expect(screen.getByText("50 %")).toBeTruthy() // 17/20 réussie, 8/20 non
    expect(screen.getByText("1")).toBeTruthy()    // une seule évaluation disponible maintenant
    expect(screen.queryByText(/rang/i)).toBeNull()
  })

  it("aucun résultat publié : indicateurs à « — » ; aucune évaluation : message", async () => {
    repondre([], { historique: [], moyenneGenerale: null, moyennePromotionGenerale: null, ecartMoyenne: null, nbEvaluations: 0 })
    afficher()

    expect(await screen.findByText("Aucune évaluation pour votre promotion pour le moment.")).toBeTruthy()
    expect(screen.getAllByText("—").length).toBe(2)
    expect(screen.getByText("0")).toBeTruthy()
  })
})

describe("aucune donnée simulée sur l'accueil", () => {
  const sources = import.meta.glob(["./DashboardEtudiant.tsx"], { query: "?raw", import: "default", eager: true }) as Record<string, string>

  it.each(Object.keys(sources))("%s n'importe aucun mock et n'en déclare pas", (fichier) => {
    expect(sources[fichier]).not.toContain("@/mocks/")
    expect(sources[fichier]).not.toMatch(/mock/i)
  })
})
