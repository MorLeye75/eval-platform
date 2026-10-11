import { useState, useEffect, useRef } from "react"

interface UseAutoSaveOptions<T> {
  data: T
  intervalMs?: number
  onSave?: (data: T) => void
  enabled?: boolean
}

/**
 * Hook gérant la sauvegarde automatique des réponses de l'étudiant
 * à intervalle régulier (par défaut 30 secondes).
 */
export function useAutoSave<T>({
  data,
  intervalMs = 30000,
  onSave,
  enabled = true,
}: UseAutoSaveOptions<T>) {
  const [derniereSauvegarde, setDerniereSauvegarde] = useState<string | null>(null)
  const dataRef = useRef(data)
  dataRef.current = data

  const onSaveRef = useRef(onSave)
  onSaveRef.current = onSave

  useEffect(() => {
    if (!enabled) return

    const autoSave = setInterval(() => {
      const maintenant = new Date().toLocaleTimeString("fr-FR", {
        hour: "2-digit",
        minute: "2-digit",
      })
      setDerniereSauvegarde(maintenant)

      if (onSaveRef.current) {
        onSaveRef.current(dataRef.current)
      } else {
        console.log("Sauvegarde auto (mock) :", dataRef.current)
      }
    }, intervalMs)

    return () => clearInterval(autoSave)
  }, [enabled, intervalMs])

  return {
    derniereSauvegarde,
    setDerniereSauvegarde,
  }
}
