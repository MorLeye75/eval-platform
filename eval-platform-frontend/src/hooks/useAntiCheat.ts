import { useState, useEffect, useRef } from "react"

interface UseAntiCheatOptions {
  /** Sorties déjà enregistrées par le serveur (copie reprise après un rechargement). */
  initialCount?: number
  /** Appelé une fois à chaque sortie de la page (pour la signaler au serveur). */
  onTabSwitch?: () => void
  enabled?: boolean
}

/**
 * Hook de détection anti-triche : surveille les changements d'onglet et la visibilité de la fenêtre
 * de passation. Le compteur repart de celui du serveur, qui ne déclenche aucun signalement ; seules
 * les nouvelles sorties sont signalées.
 */
export function useAntiCheat({ initialCount = 0, onTabSwitch, enabled = true }: UseAntiCheatOptions = {}) {
  const [changementsOnglet, setChangementsOnglet] = useState(initialCount)
  const onTabSwitchRef = useRef(onTabSwitch)
  useEffect(() => {
    onTabSwitchRef.current = onTabSwitch
  })

  // Compteur du serveur reçu après le montage (copie chargée) : jamais en dessous, sans signalement
  useEffect(() => {
    setChangementsOnglet((n) => Math.max(n, initialCount))
  }, [initialCount])

  useEffect(() => {
    if (!enabled) return

    function handleVisibilite() {
      if (!document.hidden) return
      setChangementsOnglet((n) => n + 1)
      onTabSwitchRef.current?.()
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
