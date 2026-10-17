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
  useEffect(() => {
    onVisibilityChangeRef.current = onVisibilityChange
  })

  useEffect(() => {
    if (!enabled) return

    function handleVisibilite() {
      if (!document.hidden) return
      setChangementsOnglet((prev) => prev + 1)
    }

    document.addEventListener("visibilitychange", handleVisibilite)
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilite)
    }
  }, [enabled])

  // Notification hors de la fonction de mise à jour (une seule fois par sortie)
  useEffect(() => {
    if (changementsOnglet > 0) onVisibilityChangeRef.current?.(changementsOnglet)
  }, [changementsOnglet])

  return {
    changementsOnglet,
    setChangementsOnglet,
  }
}
