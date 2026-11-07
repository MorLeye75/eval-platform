// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import ListeEvaluationsEnseignant from "./ListeEvaluationsEnseignant"
import ValidationIA from "./ValidationIA"
import PublicationResultats from "./PublicationResultats"
import BuilderQuestions from "./creation/BuilderQuestions"
import { CLE_BROUILLON_EVALUATION } from "@/lib/session"

/** Accessibilité clavier et lecteur d'écran : vrais liens et boutons, avec un nom et un état. */
function afficher(element: React.ReactElement, chemin = "/", motif = "/") {
  render(
    <MemoryRouter initialEntries={[chemin]}>
      <Routes>
        <Route path={motif} element={element} />
      </Routes>
    </MemoryRouter>
  )
}

describe("accessibilité des écrans enseignant", () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  afterEach(() => {
    cleanup()
  })

  it("liste des évaluations : actions en vrais liens nommés, sans bouton imbriqué dans un lien", () => {
    afficher(<ListeEvaluationsEnseignant />)

    const liens = [
      ...screen.getAllByRole("link", { name: /^Statistiques : / }),
      ...screen.getAllByRole("link", { name: /^Supervision : / }),
      ...screen.getAllByRole("link", { name: /^Modifier : / }),
    ]
    expect(liens.length).toBeGreaterThan(0)
    liens.forEach((lien) => expect(lien.querySelector("button")).toBeNull())
    expect(screen.getAllByRole("button", { name: /^Dupliquer : / }).length).toBeGreaterThan(0)
  })

  it("validation IA : chaque copie s'ouvre par un bouton qui annonce son état (aria-expanded)", () => {
    afficher(<ValidationIA />)
    const entete = screen.getAllByRole("button", { expanded: false })[0]

    fireEvent.click(entete)

    expect(entete.getAttribute("aria-expanded")).toBe("true")
  })

  it("publication des résultats : la sélection d'une évaluation est un bouton à état (aria-pressed)", () => {
    afficher(<PublicationResultats />)
    const carte = screen.getAllByRole("button", { pressed: false })[0]

    fireEvent.click(carte)

    expect(carte.getAttribute("aria-pressed")).toBe("true")
  })

  it("builder : question dépliable par un bouton, actions de la question nommées", () => {
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify({
      evaluationId: null,
      infos: {
        titre: "Structures de données", description: "Évaluation de test.", duree: 60,
        dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T18:00", statut: "BROUILLON",
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
