// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { CLE_BROUILLON_EVALUATION } from "@/lib/session"
import Previsualisation from "./Previsualisation"

const BROUILLON = {
  evaluationId: null,
  infos: {
    titre: "Structures de données", description: "QCM sur les piles et files.", duree: 60,
    dateOuverture: "2030-01-10T08:00", dateFermeture: "2030-01-10T18:00", statut: "PUBLIEE",
  },
  questions: [{
    localId: 1, type: "QCM", enonce: "Structure LIFO ?", points: 2, estChoixMultiple: false,
    choix: [{ id: 1, texte: "Pile", estCorrect: true }, { id: 2, texte: "File", estCorrect: false }],
  }],
}

describe("prévisualisation : publication tant que l'API de création n'existe pas", () => {
  beforeEach(() => {
    sessionStorage.clear()
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify(BROUILLON))
  })

  afterEach(() => {
    cleanup()
  })

  it("le bouton de publication est désactivé et le brouillon n'est jamais effacé sans enregistrement", () => {
    render(
      <MemoryRouter initialEntries={["/enseignant/evaluations/creation/previsualisation"]}>
        <Routes>
          <Route path="/enseignant/evaluations/creation/previsualisation" element={<Previsualisation />} />
        </Routes>
      </MemoryRouter>
    )

    const publier = screen.getByRole("button", { name: /Publier l'évaluation/ }) as HTMLButtonElement
    expect(publier.disabled).toBe(true)
    fireEvent.click(publier)
    expect(sessionStorage.getItem(CLE_BROUILLON_EVALUATION)).not.toBeNull()
    expect(screen.getByText(/votre brouillon est conservé/i)).toBeTruthy()
  })
})
