// @vitest-environment jsdom
import { act, cleanup, renderHook } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { useAntiCheat } from "@/hooks/useAntiCheat"

/** Simule le passage de la page en arrière-plan (onglet changé, fenêtre réduite) ou son retour. */
function visibilite(cachee: boolean) {
  Object.defineProperty(document, "hidden", { configurable: true, get: () => cachee })
  act(() => {
    document.dispatchEvent(new Event("visibilitychange"))
  })
}

interface Props {
  initialCount: number
  enabled: boolean
}

describe("useAntiCheat", () => {
  afterEach(() => {
    cleanup()
    visibilite(false)
  })

  it("reprend le compteur du serveur sans le signaler à nouveau (rechargement de la page)", () => {
    const onTabSwitch = vi.fn()
    const { result, rerender } = renderHook(
      ({ initialCount, enabled }: Props) => useAntiCheat({ initialCount, onTabSwitch, enabled }),
      { initialProps: { initialCount: 0, enabled: false } }
    )

    rerender({ initialCount: 3, enabled: true }) // copie reçue du serveur

    expect(result.current.changementsOnglet).toBe(3)
    expect(onTabSwitch).not.toHaveBeenCalled()
  })

  it("chaque sortie de la page est comptée et signalée une fois ; le retour sur la page ne compte pas", () => {
    const onTabSwitch = vi.fn()
    const { result } = renderHook(() => useAntiCheat({ initialCount: 1, onTabSwitch }))

    visibilite(true)
    visibilite(false)

    expect(result.current.changementsOnglet).toBe(2)
    expect(onTabSwitch).toHaveBeenCalledTimes(1)
  })

  it("désactivé (copie remise, envoi en cours) : rien n'est compté ni signalé", () => {
    const onTabSwitch = vi.fn()
    const { result } = renderHook(() => useAntiCheat({ initialCount: 0, onTabSwitch, enabled: false }))

    visibilite(true)

    expect(result.current.changementsOnglet).toBe(0)
    expect(onTabSwitch).not.toHaveBeenCalled()
  })
})
