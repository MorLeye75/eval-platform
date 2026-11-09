// @vitest-environment jsdom
import { act, cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getEvaluationEnseignant } from "@/mocks/evaluationsEnseignant"
import SupervisionTempsReel from "./SupervisionTempsReel"

// Espion sur la lecture de l'évaluation : elle a lieu à chaque rendu de la page
vi.mock("@/mocks/evaluationsEnseignant", async (original) => {
  const module = await original<typeof import("@/mocks/evaluationsEnseignant")>()
  return { ...module, getEvaluationEnseignant: vi.fn(module.getEvaluationEnseignant) }
})

describe("supervision en temps réel : compte à rebours", () => {
  beforeEach(() => {
    // L'évaluation 1 ferme le 2026-07-12 à 23:59 (heure locale)
    vi.useFakeTimers({ now: new Date("2026-07-12T23:00:00") })
    vi.mocked(getEvaluationEnseignant).mockClear()
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it("le compte à rebours avance chaque seconde sans redessiner toute la page", () => {
    render(
      <MemoryRouter initialEntries={["/enseignant/supervision/1"]}>
        <Routes>
          <Route path="/enseignant/supervision/:id" element={<SupervisionTempsReel />} />
        </Routes>
      </MemoryRouter>
    )
    expect(screen.getByText("59m 0s")).toBeTruthy()
    const rendusPage = vi.mocked(getEvaluationEnseignant).mock.calls.length

    act(() => {
      vi.advanceTimersByTime(3000)
    })

    expect(screen.getByText("58m 57s")).toBeTruthy()
    expect(vi.mocked(getEvaluationEnseignant).mock.calls.length).toBe(rendusPage)
  })
})
