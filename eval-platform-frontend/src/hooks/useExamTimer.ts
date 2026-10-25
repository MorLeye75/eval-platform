import { useState, useEffect, useRef } from "react"

interface UseExamTimerOptions {
  dureeSecondes: number
  onExpire: () => void
  enabled?: boolean
}

export function formatTemps(secondesTotal: number) {
  const minutes = Math.floor(secondesTotal / 60)
  const secondes = secondesTotal % 60
  return `${String(minutes).padStart(2, "0")}:${String(secondes).padStart(2, "0")}`
}

/**
 * Hook gérant le chronomètre d'une épreuve avec déclenchement automatique
 * à l'expiration du temps alloué.
 *
 * Le temps restant est calculé à partir d'une heure de fin fixée une seule fois
 * (pas de dérive si l'onglet est en arrière-plan) et `onExpire` est lu via une ref
 * (l'intervalle n'est pas recréé à chaque rendu).
 */
export function useExamTimer({
  dureeSecondes,
  onExpire,
  enabled = true,
}: UseExamTimerOptions) {
  const [finA, setFinA] = useState<number | null>(null)
  const [tempsRestant, setTempsRestant] = useState(dureeSecondes)
  const onExpireRef = useRef(onExpire)
  useEffect(() => {
    onExpireRef.current = onExpire
  })

  // Fixe l'heure de fin une seule fois, au démarrage
  useEffect(() => {
    if (enabled && finA === null) setFinA(Date.now() + dureeSecondes * 1000)
  }, [enabled, finA, dureeSecondes])

  useEffect(() => {
    if (finA === null) return
    const tick = () =>
      setTempsRestant(Math.max(0, Math.round((finA - Date.now()) / 1000)))
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [finA])

  // Réaction à l'expiration dans un effet séparé (la mise à jour d'état reste pure)
  useEffect(() => {
    if (finA !== null && tempsRestant === 0) onExpireRef.current()
  }, [finA, tempsRestant])

  return {
    tempsRestant,
    tempsRestantFormate: formatTemps(tempsRestant),
  }
}
