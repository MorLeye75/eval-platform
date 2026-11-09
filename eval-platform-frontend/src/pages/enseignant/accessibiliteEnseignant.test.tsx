// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import ListeEvaluationsEnseignant from "./ListeEvaluationsEnseignant"
import ValidationIA from "./ValidationIA"
import PublicationResultats from "./PublicationResultats"
import BuilderQuestions from "./creation/BuilderQuestions"
import { CLE_BROUILLON_EVALUATION } from "@/lib/session"
import { mesEvaluationsEnseignant, type EvaluationEnseignantApi } from "@/lib/evaluationsEnseignant"

// La liste lit l'API : on la remplace par quelques évaluations dans chaque état
vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  mesEvaluationsEnseignant: vi.fn(),
  listerPromotions: vi.fn().mockResolvedValue([]),
}))

const evaluation = (id: number, statut: EvaluationEnseignantApi["statut"], nbCopies: number): EvaluationEnseignantApi => ({
  id, titre: `Évaluation ${id}`, description: null, dureeStandard: 60, dateOuverture: "2030-01-10T08:00:00",
  dateFermeture: "2030-01-10T18:00:00", statut, resultatsPublies: false, version: 0, promotionIds: [1],
  nbQuestions: 3, nbCopies, nbAttendus: 45, questions: [], amenagements: [],
})

/** Accessibilité clavier et lecteur d'écran : vrais liens et boutons, avec un nom et un état. */
function afficher(element: React.ReactElement, chemin = "/", motif = "/") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[chemin]}>
        <Routes>
          <Route path={motif} element={element} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("accessibilité des écrans enseignant", () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(mesEvaluationsEnseignant).mockResolvedValue([
      evaluation(1, "BROUILLON", 0), evaluation(2, "PUBLIEE", 0), evaluation(3, "EN_COURS", 12), evaluation(4, "TERMINEE", 40),
    ])
  })

  afterEach(() => {
    cleanup()
  })

  it("liste des évaluations : actions en vrais liens nommés, sans bouton imbriqué dans un lien", async () => {
    afficher(<ListeEvaluationsEnseignant />)

    const liens = [
      ...(await screen.findAllByRole("link", { name: /^Statistiques : / })),
      ...screen.getAllByRole("link", { name: /^Supervision : / }),
      ...screen.getAllByRole("link", { name: /^Modifier : / }),
    ]
    expect(liens.length).toBeGreaterThan(0)
    liens.forEach((lien) => expect(lien.querySelector("button")).toBeNull())
    // Les actions qui modifient une évaluation sont de vrais boutons nommés
    expect(screen.getByRole("button", { name: "Publier : Évaluation 1" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Supprimer : Évaluation 1" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Repasser en brouillon : Évaluation 2" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Publier les résultats : Évaluation 4" })).toBeTruthy()
  })

  it("validation IA : chaque copie s'ouvre par un bouton qui annonce son état (aria-expanded)", () => {
    afficher(<ValidationIA />)
    const entete = screen.getAllByRole("button", { expanded: false })[0]

    fireEvent.click(entete)

    expect(entete.getAttribute("aria-expanded")).toBe("true")
  })

  it("publication des résultats : la sélection d'une évaluation est un bouton à état (aria-pressed)", async () => {
    afficher(<PublicationResultats />)
    const carte = (await screen.findAllByRole("button", { pressed: false }))[0]

    fireEvent.click(carte)

    expect(carte.getAttribute("aria-pressed")).toBe("true")
  })

  it("builder : question dépliable par un bouton, actions de la question nommées", () => {
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify({
      evaluationId: null,
      infos: {
        titre: "Structures de données", description: "Évaluation de test.", duree: 60,
        dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T18:00", statut: "BROUILLON", promotionIds: [1],
      },
      questions: [{ localId: 1, type: "OUVERTE", enonce: "Expliquez la récursivité.", points: 2 }],
    }))
    afficher(<BuilderQuestions />, "/enseignant/evaluations/creation/questions", "/enseignant/evaluations/creation/questions")

    const entete = screen.getByRole("button", { name: /Expliquez la récursivité/, expanded: false })
    fireEvent.click(entete)
    expect(entete.getAttribute("aria-expanded")).toBe("true")

    expect(screen.getByRole("button", { name: "Monter la question 1" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Descendre la question 1" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Supprimer la question 1" })).toBeTruthy()
  })
})
