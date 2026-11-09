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
 * Le temps restant est calculé à partir d'une heure de fin (pas de dérive si l'onglet est en
 * arrière-plan), fixée au démarrage et recalée si le serveur renvoie un autre temps restant
 * (copie rechargée). `onExpire` est lu via une ref (l'intervalle n'est pas recréé à chaque rendu).
 *
 * L'expiration est détectée dans le tick, à partir du temps calculé, et jamais à partir de l'état
 * `tempsRestant` : celui-ci vaut encore sa valeur initiale (0 tant que la copie n'est pas chargée)
 * au rendu où l'heure de fin est fixée, ce qui soumettait la copie vide dès son ouverture.
 */
export function useExamTimer({
  dureeSecondes,
  onExpire,
  enabled = true,
}: UseExamTimerOptions) {
  const [finA, setFinA] = useState<number | null>(null)
  const [tempsRestant, setTempsRestant] = useState(Math.max(0, dureeSecondes))

  // Durée reçue (copie chargée ou rechargée) : le temps affiché suit dans le même rendu. Recalé seulement dans
  // l'effet, il montrait « 00:00 » en rouge le temps d'un affichage à l'ouverture de l'épreuve.
  const [dureeSuivie, setDureeSuivie] = useState(dureeSecondes)
  const dureeChangee = dureeSecondes !== dureeSuivie
  if (dureeChangee) {
    setDureeSuivie(dureeSecondes)
    setTempsRestant(Math.max(0, dureeSecondes))
  }
  const tempsAffiche = dureeChangee ? Math.max(0, dureeSecondes) : tempsRestant
  const expirationDeclenchee = useRef(false)
  const onExpireRef = useRef(onExpire)
  useEffect(() => {
    onExpireRef.current = onExpire
  })

  useEffect(() => {
    if (!enabled) {
      setFinA(null)
      return
    }
    setFinA(Date.now() + Math.max(0, dureeSecondes) * 1000)
    expirationDeclenchee.current = false
  }, [enabled, dureeSecondes])

  useEffect(() => {
    if (finA === null) return
    const tick = () => {
      const restant = Math.max(0, Math.round((finA - Date.now()) / 1000))
      setTempsRestant(restant)
      if (restant === 0 && !expirationDeclenchee.current) {
        expirationDeclenchee.current = true
        onExpireRef.current()
      }
    }
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [finA])

  return {
    tempsRestant: tempsAffiche,
    tempsRestantFormate: formatTemps(tempsAffiche),
  }
}
