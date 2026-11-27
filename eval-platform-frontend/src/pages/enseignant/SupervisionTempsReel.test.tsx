// @vitest-environment jsdom
import { act, cleanup, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { mesEvaluationsEnseignant, type EvaluationEnseignantApi } from "@/lib/evaluationsEnseignant"
import { INTERVALLE_SUPERVISION_MS, pourcentage, supervisionEvaluation, tempsRestant, type SupervisionApi } from "@/lib/supervision"
import SupervisionTempsReel from "./SupervisionTempsReel"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
}))
vi.mock("@/lib/supervision", async (original) => ({
  ...(await original<typeof import("@/lib/supervision")>()),
  supervisionEvaluation: vi.fn(),
}))

const evaluation = (id: number, statut: EvaluationEnseignantApi["statut"]): EvaluationEnseignantApi => ({
  id, titre: `Évaluation ${id}`, description: null, dureeStandard: 60, dateOuverture: "2026-07-12T08:00:00",
  dateFermeture: "2026-07-12T23:59:00", statut, resultatsPublies: false, version: 0, promotionIds: [1],
  nbQuestions: 3, nbCopies: 2, nbAttendus: 3, questions: [], amenagements: [],
})

const SUPERVISION: SupervisionApi = {
  evaluationId: 1, titre: "Évaluation 1", statut: "EN_COURS", dateOuverture: "2026-07-12T08:00:00", dateFermeture: "2026-07-12T23:59:00",
  nbQuestions: 3, nbAttendus: 3, nbCommences: 2, nbSoumis: 1, nbAlertes: 1,
  etudiants: [
    { etudiantId: 1, nom: "Ba", prenom: "Cheikh", etat: "EN_COURS", nbReponses: 2, nbChangementsOnglet: 4, nbAlertes: 1 },
    { etudiantId: 2, nom: "Diop", prenom: "Moussa", etat: "SOUMIS", nbReponses: 3, nbChangementsOnglet: 0, nbAlertes: 0 },
    { etudiantId: 3, nom: "Sy", prenom: "Awa", etat: "NON_COMMENCE", nbReponses: 0, nbChangementsOnglet: 0, nbAlertes: 0 },
  ],
  alertes: [{ id: 9, etudiantId: 1, etudiant: "Cheikh Ba", description: "Sorties d'écran répétées (3)", niveau: "MODERE", date: "2026-07-12T22:40:00" }],
}

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher(chemin = "/enseignant/supervision/1") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[chemin]}>
        <Routes><Route path="/enseignant/supervision/:id?" element={<SupervisionTempsReel />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("supervision en temps réel (DS-17)", () => {
  beforeEach(() => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(1, "EN_COURS"), evaluation(2, "TERMINEE"), evaluation(3, "BROUILLON")])
    vi.mocked(supervisionEvaluation).mockResolvedValue(SUPERVISION)
  })
  afterEach(() => { cleanup(); vi.useRealTimers(); vi.clearAllMocks() })

  it("affiche l'avancement de l'API : effectifs, progression des étudiants, alertes, avertissement « indices »", async () => {
    afficher()

    expect(await screen.findByText("Cheikh Ba", { selector: "span" })).toBeTruthy()
    expect(screen.getByText("Moussa Diop")).toBeTruthy()
    expect(screen.getByText("2/3")).toBeTruthy()          // étudiant en cours : 2 réponses sur 3
    expect(screen.getByText("Rendu")).toBeTruthy()
    expect(screen.getByText("Pas commencé")).toBeTruthy()
    expect(screen.getByText("4 sorties d'écran")).toBeTruthy()
    expect(screen.getByText("Sorties d'écran répétées (3)")).toBeTruthy()
    expect(screen.getByText(/Des indices, pas des preuves/)).toBeTruthy()
    expect(screen.getByText("33% de complétion")).toBeTruthy()
    expect(supervisionEvaluation).toHaveBeenCalledWith("1")
  })

  it("compte à rebours jusqu'à la fermeture", async () => {
    vi.useFakeTimers({ now: new Date("2026-07-12T23:00:00"), toFake: ["Date"] })
    afficher()
    expect(await screen.findByText("59m 0s")).toBeTruthy()
  })

  it("sans identifiant : prend la première évaluation en cours", async () => {
    afficher("/enseignant/supervision")
    await screen.findByText("Moussa Diop")
    expect(supervisionEvaluation).toHaveBeenCalledWith("1")
  })

  it("aucune évaluation en cours : message clair", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(2, "TERMINEE"), evaluation(3, "BROUILLON")])
    afficher("/enseignant/supervision")
    expect(await screen.findByText("Aucune évaluation n'est en cours actuellement.")).toBeTruthy()
  })

  it("évaluation pas encore ouverte (409) : message du serveur", async () => {
    vi.mocked(supervisionEvaluation).mockRejectedValue(erreurHttp(409, "L'évaluation n'est pas encore ouverte : il n'y a rien à superviser."))
    afficher("/enseignant/supervision/3")
    expect(await screen.findByText("L'évaluation n'est pas encore ouverte : il n'y a rien à superviser.")).toBeTruthy()
  })

  it("sondage : relit toutes les 15 s tant que l'évaluation est en cours, garde l'état précédent si une lecture échoue", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    afficher()
    await screen.findByText("Moussa Diop")
    expect(supervisionEvaluation).toHaveBeenCalledTimes(1)

    vi.mocked(supervisionEvaluation).mockRejectedValueOnce(new Error("réseau"))
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALLE_SUPERVISION_MS + 100) })

    expect(supervisionEvaluation).toHaveBeenCalledTimes(2)
    expect(await screen.findByText(/Données non actualisées/)).toBeTruthy()
    expect(screen.getByText("Moussa Diop")).toBeTruthy()
  })

  it("évaluation terminée : plus de sondage", async () => {
    vi.mocked(supervisionEvaluation).mockResolvedValue({ ...SUPERVISION, statut: "TERMINEE" })
    vi.useFakeTimers({ shouldAdvanceTime: true })
    afficher("/enseignant/supervision/2")
    await screen.findByText(/évaluation terminée/)

    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALLE_SUPERVISION_MS * 3) })

    expect(supervisionEvaluation).toHaveBeenCalledTimes(1)
  })
})

describe("calculs de la supervision", () => {
  it("temps restant : minutes et secondes, « Terminé » une fois la fermeture passée", () => {
    const fermeture = "2026-07-12T23:59:00"
    expect(tempsRestant(fermeture, new Date("2026-07-12T23:00:00").getTime())).toBe("59m 0s")
    expect(tempsRestant(fermeture, new Date("2026-07-12T23:58:30").getTime())).toBe("0m 30s")
    expect(tempsRestant(fermeture, new Date("2026-07-13T00:00:00").getTime())).toBe("Terminé")
  })

  it("pourcentage : arrondi, 0 sans question", () => {
    expect(pourcentage(1, 3)).toBe(33)
    expect(pourcentage(3, 3)).toBe(100)
    expect(pourcentage(2, 0)).toBe(0)
  })
})
