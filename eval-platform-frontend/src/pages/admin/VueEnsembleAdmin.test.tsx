// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  pourcent, statistiquesGlobales, supervisionGlobale, sur20, tableauDeBordAdmin,
  type StatistiquesGlobalesApi, type SupervisionGlobaleApi, type TableauDeBordApi,
} from "@/lib/adminVue"
import DashboardAdmin from "./DashboardAdmin"
import SupervisionGlobale from "./SupervisionGlobale"
import StatistiquesGlobales from "./StatistiquesGlobales"

vi.mock("recharts", async (original) => {
  const r = await original<typeof import("recharts")>()
  return { ...r, ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }
})
vi.mock("@/lib/adminVue", async (original) => ({
  ...(await original<typeof import("@/lib/adminVue")>()),
  tableauDeBordAdmin: vi.fn(),
  supervisionGlobale: vi.fn(),
  statistiquesGlobales: vi.fn(),
}))

const TABLEAU: TableauDeBordApi = {
  nbUtilisateurs: 342, parRole: { etudiants: 298, enseignants: 38, admins: 6 }, nbEvaluationsEnCours: 7, nbEvaluationsPubliees: 2,
  nbSoumissions7j: 1284, nbAlertes7j: 3,
  services: [{ nom: "Base de données", etat: "OPERATIONNEL", detail: null }, { nom: "Correction IA", etat: "CONFIGURE", detail: "fournisseur : simule" }],
}
const SUPERVISION: SupervisionGlobaleApi = {
  evaluations: [
    { id: 1, titre: "Algorithmique", enseignant: "Fatou Sarr", nbAttendus: 40, nbCommences: 30, nbSoumis: 10, dateOuverture: "2026-07-20T08:00:00", dateFermeture: "2026-07-20T10:00:00" },
  ],
  alertes: [{ id: 9, evaluationId: 1, evaluation: "Algorithmique", etudiant: "Cheikh Ba", description: "Sorties d'écran répétées (3)", niveau: "ELEVE", date: "2026-07-20T09:00:00" }],
}
const STATS: StatistiquesGlobalesApi = {
  nbEvaluations: 12, nbCopies: 100, nbCopiesNotees: 80, tauxRemise: 95, moyenneSur20: 13.4, tauxReussite: 78,
  promotions: [
    { id: 1, nom: "DIC1", annee: "2025-2026", effectif: 45, nbEvaluations: 4, nbCopiesNotees: 80, moyenneSur20: 13.4, tauxReussite: 78 },
    { id: 2, nom: "DIC2", annee: "2025-2026", effectif: 38, nbEvaluations: 0, nbCopiesNotees: 0, moyenneSur20: null, tauxReussite: null },
  ],
}

function afficher(page: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter>{page}</MemoryRouter></QueryClientProvider>)
}
function carte(libelle: string) {
  return within(screen.getByText(libelle).closest("[data-slot=card]") as HTMLElement)
}

describe("vue d'ensemble de l'administrateur (DS-18)", () => {
  beforeEach(() => {
    vi.mocked(tableauDeBordAdmin).mockResolvedValue(TABLEAU)
    vi.mocked(supervisionGlobale).mockResolvedValue(SUPERVISION)
    vi.mocked(statistiquesGlobales).mockResolvedValue(STATS)
  })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("tableau de bord : chiffres du serveur et services réellement connus", async () => {
    afficher(<DashboardAdmin />)
    expect(await screen.findByText("Opérationnel")).toBeTruthy()
    expect(carte("Comptes actifs").getByText("342")).toBeTruthy()
    expect(carte("Évaluations en cours").getByText("7")).toBeTruthy()
    expect(screen.getByText("fournisseur : simule")).toBeTruthy()
    expect(screen.queryByText(/Judge0/)).toBeNull()   // aucun état inventé
  })

  it("tableau de bord : « — » pendant le chargement, jamais un 0", () => {
    vi.mocked(tableauDeBordAdmin).mockReturnValue(new Promise(() => {}))
    afficher(<DashboardAdmin />)
    expect(carte("Comptes actifs").getByText("—")).toBeTruthy()
  })

  it("tableau de bord : erreur affichée", async () => {
    vi.mocked(tableauDeBordAdmin).mockRejectedValue(new Error("réseau"))
    afficher(<DashboardAdmin />)
    expect(await screen.findByRole("alert")).toBeTruthy()
  })

  it("supervision : évaluations en cours avec effectifs, alertes avec étudiant et évaluation", async () => {
    afficher(<SupervisionGlobale />)
    expect(await screen.findByText("Algorithmique", { selector: "div.font-semibold" })).toBeTruthy()
    expect(screen.getByText(/Fatou Sarr · 30 \/ 40 copies commencées · 10 remises/)).toBeTruthy()
    expect(screen.getByText("75%")).toBeTruthy()
    expect(screen.getByText("Sorties d'écran répétées (3)")).toBeTruthy()
    expect(screen.getByText("Cheikh Ba · Algorithmique")).toBeTruthy()
    expect(screen.getByText(/indices, pas des preuves/)).toBeTruthy()
  })

  it("supervision : rien en cours, message clair", async () => {
    vi.mocked(supervisionGlobale).mockResolvedValue({ evaluations: [], alertes: [] })
    afficher(<SupervisionGlobale />)
    expect(await screen.findByText("Aucune évaluation n'est en cours actuellement.")).toBeTruthy()
    expect(screen.getByText("Aucune alerte.")).toBeTruthy()
  })

  it("statistiques : moyenne sur 20, et « — » pour une promotion sans copie notée", async () => {
    afficher(<StatistiquesGlobales />)
    expect(await screen.findByText("DIC2", { exact: false })).toBeTruthy()
    expect(carte("Moyenne plateforme").getByText("13,4 / 20")).toBeTruthy()
    expect(carte("Taux de réussite").getByText("78 %")).toBeTruthy()
    const ligne = screen.getByText("DIC2", { exact: false }).closest("tr") as HTMLElement
    expect(within(ligne).getAllByText("—").length).toBe(2)
    expect(within(ligne).queryByText("0 %")).toBeNull()
  })

  it("statistiques : aucune copie notée, indicateurs « — » et message", async () => {
    vi.mocked(statistiquesGlobales).mockResolvedValue({ ...STATS, nbCopiesNotees: 0, moyenneSur20: null, tauxReussite: null, promotions: [] })
    afficher(<StatistiquesGlobales />)
    expect(await screen.findByText("Aucune copie notée pour l'instant.")).toBeTruthy()
    expect(carte("Moyenne plateforme").getByText("—")).toBeTruthy()
  })
})

describe("formats", () => {
  it("sur20 et pourcent : virgule française, « — » pour null", () => {
    expect(sur20(13.4)).toBe("13,4 / 20")
    expect(sur20(null)).toBe("—")
    expect(pourcent(78)).toBe("78 %")
    expect(pourcent(null)).toBe("—")
  })
})
