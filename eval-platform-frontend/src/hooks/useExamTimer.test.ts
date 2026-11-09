// @vitest-environment jsdom
import { act, cleanup, renderHook } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useExamTimer } from "@/hooks/useExamTimer"

interface Props {
  dureeSecondes: number
  enabled: boolean
}

describe("useExamTimer (DS-01 fin du chrono)", () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it("ne déclenche pas l'expiration quand la durée arrive après le montage (copie chargée depuis le serveur)", () => {
    const onExpire = vi.fn()
    // Comme usePassation : 0 s et désactivé tant que la copie n'est pas chargée
    const { result, rerender } = renderHook(
      ({ dureeSecondes, enabled }: Props) => useExamTimer({ dureeSecondes, onExpire, enabled }),
      { initialProps: { dureeSecondes: 0, enabled: false } }
    )

    rerender({ dureeSecondes: 1800, enabled: true })

    expect(onExpire).not.toHaveBeenCalled()
    expect(result.current.tempsRestantFormate).toBe("30:00")
  })

  it("aucun rendu ne montre 00:00 quand la copie arrive (pas de flash rouge à l'ouverture)", () => {
    const affiches: string[] = []
    const { rerender } = renderHook(
      ({ dureeSecondes, enabled }: Props) => {
        const r = useExamTimer({ dureeSecondes, onExpire: () => {}, enabled })
        affiches.push(r.tempsRestantFormate)
        return r
      },
      { initialProps: { dureeSecondes: 0, enabled: false } }
    )
    affiches.length = 0 // avant le chargement, l'écran du chrono n'est pas affiché

    rerender({ dureeSecondes: 1800, enabled: true })

    expect(affiches.length).toBeGreaterThan(0)
    expect(affiches).not.toContain("00:00")
  })

  it("déclenche l'expiration une seule fois quand le temps est écoulé", () => {
    const onExpire = vi.fn()
    const { result } = renderHook(() => useExamTimer({ dureeSecondes: 3, onExpire }))

    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(onExpire).not.toHaveBeenCalled()
    expect(result.current.tempsRestant).toBe(1)

    act(() => {
      vi.advanceTimersByTime(5000)
    })
    expect(onExpire).toHaveBeenCalledTimes(1)
    expect(result.current.tempsRestant).toBe(0)
  })

  it("expire immédiatement une copie dont le serveur annonce 0 s restantes", () => {
    const onExpire = vi.fn()
    renderHook(() => useExamTimer({ dureeSecondes: 0, onExpire }))

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  it("ne décompte pas et n'expire pas tant qu'il est désactivé", () => {
    const onExpire = vi.fn()
    renderHook(() => useExamTimer({ dureeSecondes: 0, onExpire, enabled: false }))

    act(() => {
      vi.advanceTimersByTime(5000)
    })
    expect(onExpire).not.toHaveBeenCalled()
  })
})
