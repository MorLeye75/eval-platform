import { useState, useEffect, useRef } from "react"

interface UseAutoSaveOptions<T> {
  data: T
  intervalMs?: number
  /** Doit résoudre une fois la sauvegarde réussie, et rejeter en cas d'échec. */
  onSave?: (data: T) => void | Promise<void>
  enabled?: boolean
}

/**
 * Hook gérant la sauvegarde automatique des réponses de l'étudiant
 * à intervalle régulier (par défaut 30 secondes).
 * L'heure de dernière sauvegarde n'est mise à jour qu'après un `onSave` réussi,
 * et rien n'est sauvegardé si les données n'ont pas changé.
 */
export function useAutoSave<T>({
  data,
  intervalMs = 30000,
  onSave,
  enabled = true,
}: UseAutoSaveOptions<T>) {
  const [derniereSauvegarde, setDerniereSauvegarde] = useState<string | null>(null)
  const [erreurSauvegarde, setErreurSauvegarde] = useState(false)
  const dataRef = useRef(data)
  const onSaveRef = useRef(onSave)
  const dernierSerialise = useRef<string | null>(null)
  useEffect(() => {
    dataRef.current = data
    onSaveRef.current = onSave
  })

  useEffect(() => {
    if (!enabled) return

    const autoSave = setInterval(async () => {
      const serialise = JSON.stringify(dataRef.current)
      if (serialise === dernierSerialise.current) return

      try {
        // TODO : POST /api/soumissions/{id}/sauvegarde via onSave
        await onSaveRef.current?.(dataRef.current)
        dernierSerialise.current = serialise
        setErreurSauvegarde(false)
        setDerniereSauvegarde(
          new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })
        )
      } catch {
        setErreurSauvegarde(true)
      }
    }, intervalMs)

    return () => clearInterval(autoSave)
  }, [enabled, intervalMs])

  return {
    derniereSauvegarde,
    erreurSauvegarde,
    setDerniereSauvegarde,
  }
}
