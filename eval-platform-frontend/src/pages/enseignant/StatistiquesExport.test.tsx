// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { mesEvaluationsEnseignant, type EvaluationEnseignantApi } from "@/lib/evaluationsEnseignant"
import {
  exporterResultatsCsv,
  exporterDetailCsv,
  exporterResultatsPdf,
  statistiquesEvaluation,
  telechargerFichier,
  type StatistiquesApi,
} from "@/lib/statistiquesEnseignant"
import StatistiquesEvaluation from "./StatistiquesEvaluation"
import ExportResultats from "./ExportResultats"

vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
}))
vi.mock("@/lib/statistiquesEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/statistiquesEnseignant")>()),
  statistiquesEvaluation: vi.fn(),
  exporterResultatsCsv: vi.fn(),
  exporterDetailCsv: vi.fn(),
  exporterResultatsPdf: vi.fn(),
  telechargerFichier: vi.fn(),
}))

const evaluation = (id: number, statut: EvaluationEnseignantApi["statut"]): EvaluationEnseignantApi => ({
  id, titre: `Évaluation ${id}`, description: null, dureeStandard: 60, dateOuverture: "2030-01-10T08:00:00",
  dateFermeture: "2030-01-10T18:00:00", statut, resultatsPublies: false, version: 0, promotionIds: [1],
  nbQuestions: 3, nbCopies: 40, nbAttendus: 45, questions: [], amenagements: [],
})

const STATS: StatistiquesApi = {
  evaluationId: 3, titre: "Évaluation 3", statut: "TERMINEE", bareme: 20, nbAttendus: 45, nbCopies: 40, nbNotees: 38, nbEnCorrection: 2,
  moyenneSur20: 13.4, medianeSur20: 13, minSur20: 4, maxSur20: 19, ecartTypeSur20: 3.2, tauxReussite: 78,
  distribution: Array.from({ length: 10 }, (_, i) => ({ de: i * 2, a: i * 2 + 2, nb: i })),
  questions: [
    { questionId: 1, ordre: 1, enonce: "Facile", points: 2, moyennePoints: 1.8, tauxReussite: 90 },
    { questionId: 2, ordre: 2, enonce: "Difficile", points: 3, moyennePoints: 1, tauxReussite: 33.33 },
  ],
}

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher(page: React.ReactElement, chemin: string, motif: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[chemin]}><Routes><Route path={motif} element={page} /></Routes></MemoryRouter>
    </QueryClientProvider>
  )
}

describe("statistiques d'une évaluation (DS-09)", () => {
  beforeEach(() => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(1, "BROUILLON"), evaluation(2, "EN_COURS"), evaluation(3, "TERMINEE")])
    vi.mocked(statistiquesEvaluation).mockResolvedValue(STATS)
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("affiche les indicateurs du serveur, l'effectif et les questions les plus difficiles", async () => {
    afficher(<StatistiquesEvaluation />, "/enseignant/statistiques/3", "/enseignant/statistiques/:id?")

    expect(await screen.findByText(/^13[,.]4 \/ 20$/)).toBeTruthy()
    expect(screen.getByText(/Écart-type : 3[,.]2/)).toBeTruthy()
    expect(screen.getByText("78 %")).toBeTruthy()
    expect(screen.getByText(/19 \/ 20/)).toBeTruthy()
    expect(screen.getByText("Q2 · Difficile")).toBeTruthy()
    expect(screen.queryByText("Q1 · Facile")).toBeNull()
    expect(statistiquesEvaluation).toHaveBeenCalledWith("3")
    // copies pas encore notées : les chiffres évolueront
    expect(screen.getByRole("status").textContent).toMatch(/2 copies ne sont pas encore notées/)
  })

  it("sans identifiant : prend la première évaluation terminée", async () => {
    afficher(<StatistiquesEvaluation />, "/enseignant/statistiques", "/enseignant/statistiques/:id?")
    await screen.findByText(/^13[,.]4 \/ 20$/)
    expect(statistiquesEvaluation).toHaveBeenCalledWith("3")
  })

  it("aucune copie notée : « — » à la place des indicateurs, jamais 0", async () => {
    vi.mocked(statistiquesEvaluation).mockResolvedValue({
      ...STATS, nbNotees: 0, nbEnCorrection: 0, moyenneSur20: null, medianeSur20: null, minSur20: null, maxSur20: null,
      ecartTypeSur20: null, tauxReussite: null, questions: STATS.questions.map((q) => ({ ...q, moyennePoints: null, tauxReussite: null })),
    })
    afficher(<StatistiquesEvaluation />, "/enseignant/statistiques/3", "/enseignant/statistiques/:id?")

    expect(await screen.findByText(/Aucune copie n'est encore notée/)).toBeTruthy()
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(4)
    expect(screen.queryByText("0 / 20")).toBeNull()
  })

  it("évaluation pas encore ouverte (409) : le message du serveur est affiché", async () => {
    vi.mocked(statistiquesEvaluation).mockRejectedValue(erreurHttp(409, "Aucune copie n'est possible avant l'ouverture de l'évaluation."))
    afficher(<StatistiquesEvaluation />, "/enseignant/statistiques/1", "/enseignant/statistiques/:id?")
    expect(await screen.findByText("Aucune copie n'est possible avant l'ouverture de l'évaluation.")).toBeTruthy()
  })

  it("aucune évaluation ouverte : message clair", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(1, "BROUILLON")])
    afficher(<StatistiquesEvaluation />, "/enseignant/statistiques", "/enseignant/statistiques/:id?")
    expect(await screen.findByText("Aucune évaluation n'a encore de résultats.")).toBeTruthy()
  })
})

describe("export des résultats (DS-09)", () => {
  beforeEach(() => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([
      evaluation(1, "BROUILLON"), evaluation(2, "PUBLIEE"), evaluation(3, "EN_COURS"), evaluation(4, "TERMINEE"),
    ])
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("propose seulement les évaluations ouvertes ; le téléchargement donne le fichier au navigateur", async () => {
    const blob = new Blob(["numEtudiant;nom"])
    vi.mocked(exporterResultatsCsv).mockResolvedValue(blob)
    afficher(<ExportResultats />, "/enseignant/export", "/enseignant/export")

    expect(await screen.findByRole("button", { name: "Télécharger le CSV : Évaluation 3" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Évaluation 1/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /Évaluation 2/ })).toBeNull()

    fireEvent.click(screen.getByRole("button", { name: "Télécharger le CSV : Évaluation 4" }))
    await waitFor(() => expect(exporterResultatsCsv).toHaveBeenCalledWith(4))
    await waitFor(() => expect(telechargerFichier).toHaveBeenCalledWith(blob, "resultats-4.csv"))
    expect(await screen.findByText("Téléchargé")).toBeTruthy()
  })

  it("détail par question : un second bouton télécharge detail-<id>.csv, indépendant du CSV des résultats", async () => {
    const blob = new Blob(["numEtudiant;nom;prenom;etatCopie;ordre;type;points;noteObtenue"])
    vi.mocked(exporterDetailCsv).mockResolvedValue(blob)
    afficher(<ExportResultats />, "/enseignant/export", "/enseignant/export")

    fireEvent.click(await screen.findByRole("button", { name: "Télécharger le détail par question : Évaluation 4" }))
    await waitFor(() => expect(exporterDetailCsv).toHaveBeenCalledWith(4))
    await waitFor(() => expect(telechargerFichier).toHaveBeenCalledWith(blob, "detail-4.csv"))
    expect(exporterResultatsCsv).not.toHaveBeenCalled()
    // le CSV des résultats reste proposé, non téléchargé
    expect(screen.getByRole("button", { name: "Télécharger le CSV : Évaluation 4" })).toBeTruthy()
  })

  it("rapport PDF : un troisième bouton télécharge resultats-<id>.pdf", async () => {
    const blob = new Blob(["%PDF-1.4"], { type: "application/pdf" })
    vi.mocked(exporterResultatsPdf).mockResolvedValue(blob)
    afficher(<ExportResultats />, "/enseignant/export", "/enseignant/export")

    fireEvent.click(await screen.findByRole("button", { name: "Télécharger le PDF : Évaluation 4" }))
    await waitFor(() => expect(exporterResultatsPdf).toHaveBeenCalledWith(4))
    await waitFor(() => expect(telechargerFichier).toHaveBeenCalledWith(blob, "resultats-4.pdf"))
    expect(exporterResultatsCsv).not.toHaveBeenCalled()
  })

  it("échec de l'export : message affiché, aucun fichier téléchargé", async () => {
    vi.mocked(exporterResultatsCsv).mockRejectedValue(new Error("réseau"))
    afficher(<ExportResultats />, "/enseignant/export", "/enseignant/export")

    fireEvent.click(await screen.findByRole("button", { name: "Télécharger le CSV : Évaluation 3" }))
    expect(await screen.findByText("L'export a échoué.")).toBeTruthy()
    expect(telechargerFichier).not.toHaveBeenCalled()
  })

  it("aucune évaluation ouverte : message clair", async () => {
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([evaluation(1, "BROUILLON")])
    afficher(<ExportResultats />, "/enseignant/export", "/enseignant/export")
    expect(await screen.findByText("Aucune évaluation à exporter pour l'instant.")).toBeTruthy()
  })
})
