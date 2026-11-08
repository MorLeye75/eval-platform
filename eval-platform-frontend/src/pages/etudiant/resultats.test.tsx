// @vitest-environment jsdom
import type { ReactNode } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { AxiosError, AxiosHeaders } from "axios"
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest"
import { api } from "@/lib/axios"
import type { Performances, ResultatQuestion, ResultatsCopie } from "@/types"
import ResultatsDetail from "./ResultatsDetail"
import TableauBordPerformance from "./TableauBordPerformance"

beforeAll(() => {
  // Recharts (ResponsiveContainer) observe la taille de son conteneur ; jsdom ne fournit pas ResizeObserver
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function afficher(element: ReactNode, chemin: string, motif: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[chemin]}>
        <Routes>
          <Route path={motif} element={element} />
          <Route path="/etudiant/evaluations/:id" element={<p>Détail de l'évaluation</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function repondre(data: unknown) {
  vi.spyOn(api, "get").mockResolvedValue({ data })
}

const QCM: ResultatQuestion = {
  questionId: 1, ordre: 1, type: "QCM", enonce: "2 + 2 ?", points: 2, noteObtenue: 0, commentaire: null,
  choix: [
    { id: 10, texte: "4", coche: false, estCorrect: null },
    { id: 11, texte: "5", coche: true, estCorrect: null },
  ],
  texteSoumis: null, codeSoumis: null, langage: null, casTests: [],
}

const CODE: ResultatQuestion = {
  questionId: 2, ordre: 2, type: "CODE", enonce: "Palindrome", points: 3, noteObtenue: 1.5, commentaire: "1/2 cas réussis",
  choix: [], texteSoumis: null, codeSoumis: "print(input())", langage: "python",
  casTests: [
    { masque: false, reussi: true, verdict: "REUSSI", tempsExecution: 0.02, entree: "radar", sortieAttendue: "true",
      sortieObtenue: "true", message: null },
    { masque: true, reussi: false, verdict: "ERREUR_EXECUTION", tempsExecution: 0.05, entree: null,
      sortieAttendue: null, sortieObtenue: null, message: null },
  ],
}

function copie(r: Partial<ResultatsCopie>): ResultatsCopie {
  return {
    etat: "PUBLIES", soumissionId: 90, evaluationId: 7, titre: "Algorithmique", dateRemise: "2026-11-07T10:00:00",
    noteFinale: 1.5, totalPoints: 5, feedbackGeneral: null, moyennePromotion: null, ecartMoyenne: null,
    corrigeVisible: false, questions: [QCM, CODE], ...r,
  }
}

describe("détail des résultats d'une copie (GET /soumissions/{id}/resultats)", () => {
  it("demande les résultats de la copie de l'URL", async () => {
    repondre(copie({}))
    afficher(<ResultatsDetail />, "/etudiant/resultats/90", "/etudiant/resultats/:id")

    expect(await screen.findByText("Algorithmique")).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith("/soumissions/90/resultats")
  })

  it("copie remise non publiée : en attente, aucune note", async () => {
    repondre(copie({ etat: "EN_ATTENTE", noteFinale: null, totalPoints: null, questions: [] }))
    afficher(<ResultatsDetail />, "/etudiant/resultats/90", "/etudiant/resultats/:id")

    expect(await screen.findByText("Résultats en attente de publication")).toBeTruthy()
    expect(screen.queryByText(/Détail des réponses/)).toBeNull()
  })

  it("copie encore en cours : renvoi vers le détail de l'évaluation", async () => {
    repondre(copie({ etat: "NON_REMISE", questions: [] }))
    afficher(<ResultatsDetail />, "/etudiant/resultats/90", "/etudiant/resultats/:id")

    expect(await screen.findByText("Détail de l'évaluation")).toBeTruthy()
  })

  it("avant la fermeture : note et réponse de l'étudiant, corrigé annoncé mais pas affiché", async () => {
    repondre(copie({}))
    afficher(<ResultatsDetail />, "/etudiant/resultats/90", "/etudiant/resultats/:id")

    expect(await screen.findByText(/Les bonnes réponses seront affichées après la fermeture/)).toBeTruthy()
    expect(screen.getByText("0 / 2 pts")).toBeTruthy()
    expect(screen.getByText("Votre réponse")).toBeTruthy()
    expect(screen.queryByText("Réponse attendue")).toBeNull()
    expect(screen.getByText("Moyenne de la promotion communiquée à partir de 5 copies notées.")).toBeTruthy()
  })

  it("après la fermeture : bonne réponse signalée, moyenne et écart", async () => {
    repondre(copie({
      corrigeVisible: true, moyennePromotion: 2.5, ecartMoyenne: -1,
      questions: [{ ...QCM, choix: [
        { id: 10, texte: "4", coche: false, estCorrect: true },
        { id: 11, texte: "5", coche: true, estCorrect: false },
      ] }],
    }))
    afficher(<ResultatsDetail />, "/etudiant/resultats/90", "/etudiant/resultats/:id")

    expect(await screen.findByText("Réponse attendue")).toBeTruthy()
    expect(screen.queryByText(/Les bonnes réponses seront affichées/)).toBeNull()
    expect(screen.getByText(/Moyenne de la promotion : 2,5 \/ 5/)).toBeTruthy()
    expect(screen.getByText(/-1 point\(s\) de la moyenne/)).toBeTruthy()
  })

  it("code : cas visible détaillé, cas masqué réduit à son verdict", async () => {
    repondre(copie({}))
    afficher(<ResultatsDetail />, "/etudiant/resultats/90", "/etudiant/resultats/:id")

    expect(await screen.findByText("print(input())")).toBeTruthy()
    expect(screen.getByText("Votre code (Python)")).toBeTruthy()
    expect(screen.getByText("radar")).toBeTruthy()
    expect(screen.getByText("Cas 2 (masqué)")).toBeTruthy()
    expect(screen.getByText(/Erreur à l'exécution/)).toBeTruthy()
    expect(screen.getByText(/seul le verdict est communiqué/)).toBeTruthy()
    expect(screen.getByText("1,5 / 3 pts")).toBeTruthy()
  })

  it("copie d'un autre étudiant : message du serveur", async () => {
    vi.spyOn(api, "get").mockRejectedValue(new AxiosError("403", "ERR_BAD_REQUEST", undefined, undefined, {
      status: 403, statusText: "Forbidden", headers: {}, config: { headers: new AxiosHeaders() },
      data: { detail: "Accès non autorisé : cette copie appartient à un autre étudiant." },
    }))
    afficher(<ResultatsDetail />, "/etudiant/resultats/91", "/etudiant/resultats/:id")

    expect(await screen.findByText("Accès non autorisé : cette copie appartient à un autre étudiant.")).toBeTruthy()
  })
})

describe("tableau de bord (GET /etudiant/performances)", () => {
  it("aucun résultat publié : état vide", async () => {
    repondre({ historique: [], moyenneGenerale: null, moyennePromotionGenerale: null, ecartMoyenne: null, nbEvaluations: 0 })
    afficher(<TableauBordPerformance />, "/etudiant/performances", "/etudiant/performances")

    expect(await screen.findByText("Pas encore de résultats publiés")).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith("/etudiant/performances")
  })

  it("indicateurs, écart à la promotion, historique relié au détail de chaque copie, sans rang", async () => {
    const p: Performances = {
      historique: [
        { soumissionId: 90, evaluationId: 7, titre: "Algorithmique", date: "2026-11-01T10:00:00", noteSur20: 8, moyennePromotionSur20: 12 },
        { soumissionId: 91, evaluationId: 8, titre: "Réseaux", date: "2026-11-05T10:00:00", noteSur20: 20, moyennePromotionSur20: null },
      ],
      moyenneGenerale: 14, moyennePromotionGenerale: 12, ecartMoyenne: -4, nbEvaluations: 2,
    }
    repondre(p)
    afficher(<TableauBordPerformance />, "/etudiant/performances", "/etudiant/performances")

    expect(await screen.findByText("14 / 20")).toBeTruthy()
    expect(screen.getByText("-4 pt vs promotion")).toBeTruthy()
    expect(screen.getByText("50 %")).toBeTruthy()
    expect(screen.getByText("12 / 20")).toBeTruthy()
    expect(screen.getByText(/moyenne non communiquée \(moins de 5 copies\)/)).toBeTruthy()
    expect(screen.getByRole("link", { name: /Algorithmique/ }).getAttribute("href")).toBe("/etudiant/resultats/90")
    expect(screen.queryByText(/Rang/)).toBeNull()
  })
})

describe("aucune donnée simulée dans les écrans de résultats", () => {
  const sources = import.meta.glob(["./ResultatsDetail.tsx", "./TableauBordPerformance.tsx"], {
    query: "?raw", import: "default", eager: true,
  }) as Record<string, string>

  it.each(Object.keys(sources))("%s n'importe aucun mock", (fichier) => {
    expect(sources[fichier]).not.toContain("@/mocks/")
  })
})
