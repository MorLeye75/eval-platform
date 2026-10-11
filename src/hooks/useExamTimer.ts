import { useState, useEffect } from "react"

interface UseExamTimerOptions {
  dureeMinutes: number
  onExpire: () => void
  enabled?: boolean
}

/**
 * Hook gérant le chronomètre d'une épreuve avec déclenchement automatique
 * à l'expiration du temps alloué.
 */
export function useExamTimer({
  dureeMinutes,
  onExpire,
  enabled = true,
}: UseExamTimerOptions) {
  const [tempsRestant, setTempsRestant] = useState(dureeMinutes * 60)

  useEffect(() => {
    if (!enabled) return

    const timer = setInterval(() => {
      setTempsRestant((t) => {
        if (t <= 1) {
          clearInterval(timer)
          onExpire()
          return 0
        }
        return t - 1
      })
    }, 1000)

    return () => clearInterval(timer)
  }, [enabled, onExpire])

  const formatTemps = (secondesTotal: number) => {
    const minutes = Math.floor(secondesTotal / 60)
    const secondes = secondesTotal % 60
    return `${String(minutes).padStart(2, "0")}:${String(secondes).padStart(2, "0")}`
  }

  return {
    tempsRestant,
    tempsRestantFormate: formatTemps(tempsRestant),
    setTempsRestant,
  }
}
