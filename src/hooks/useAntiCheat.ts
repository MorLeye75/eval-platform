import { useState, useEffect, useRef } from "react"

interface UseAntiCheatOptions {
  onVisibilityChange?: (count: number) => void
  enabled?: boolean
}

/**
 * Hook de détection anti-triche : surveille les changements d'onglet
 * et la visibilité de la fenêtre de passation d'examen.
 */
export function useAntiCheat({
  onVisibilityChange,
  enabled = true,
}: UseAntiCheatOptions = {}) {
  const [changementsOnglet, setChangementsOnglet] = useState(0)
  const onVisibilityChangeRef = useRef(onVisibilityChange)
  onVisibilityChangeRef.current = onVisibilityChange

  useEffect(() => {
    if (!enabled) return

    function handleVisibilite() {
      if (document.hidden) {
        setChangementsOnglet((prev) => {
          const next = prev + 1
          onVisibilityChangeRef.current?.(next)
          return next
        })
      }
    }

    document.addEventListener("visibilitychange", handleVisibilite)
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilite)
    }
  }, [enabled])

  return {
    changementsOnglet,
    setChangementsOnglet,
  }
}
