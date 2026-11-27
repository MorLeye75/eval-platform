// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { afterEach, describe, expect, it } from "vitest"
import ConfirmationSoumission from "./ConfirmationSoumission"

describe("écran de confirmation de remise", () => {
  afterEach(() => {
    cleanup()
  })

  it("affiche l'heure de remise enregistrée par le serveur", () => {
    render(
      <MemoryRouter
        initialEntries={[{ pathname: "/", state: { titre: "Épreuve", traitees: 2, total: 3, dateRemise: "2026-11-07T10:42:13.51" } }]}
      >
        <ConfirmationSoumission />
      </MemoryRouter>
    )

    expect(screen.getByText("10:42")).toBeTruthy()
  })
})
