import { useState, useEffect, useRef, useCallback } from "react"
import { estCopieVerrouillee, formatHeure } from "@/lib/passation"

export type ResultatSauvegarde = "ok" | "verrouillee" | "echec"

interface UseAutoSaveOptions<T> {
  data: T
  intervalMs?: number
  /**
   * Envoie les données au serveur. Résout avec l'heure de sauvegarde renvoyée
   * par le serveur (ISO 8601), rejette en cas d'échec.
   */
  onSave?: (data: T) => Promise<string | void>
  /** Clé localStorage du tampon conservé tant que le serveur n'a pas confirmé la sauvegarde. */
  cleTampon?: string
  /** Le serveur refuse la sauvegarde : temps expiré ou copie qui n'est plus EN_COURS (409). */
  onVerrouillage?: () => void
  enabled?: boolean
}

export function lireTampon<T>(cle: string): T | null {
  try {
    const brut = localStorage.getItem(cle)
    return brut ? (JSON.parse(brut) as T) : null
  } catch {
    return null
  }
}

export function viderTampon(cle: string) {
  try {
    localStorage.removeItem(cle)
  } catch {
    // stockage indisponible (navigation privée) : rien à vider
  }
}

function ecrireTampon(cle: string, serialise: string) {
  try {
    localStorage.setItem(cle, serialise)
  } catch {
    // stockage plein ou indisponible : la réponse reste en mémoire pour le prochain essai
  }
}

/**
 * Hook gérant la sauvegarde automatique des réponses de l'étudiant (Diagramme de Séquence DS-02).
 *
 * - Toutes les 30 secondes (par défaut), envoie les données si elles ont changé.
 * - Succès : affiche l'heure de sauvegarde renvoyée par le serveur et vide le tampon.
 * - 409 (temps expiré ou copie verrouillée) : `onVerrouillage`, l'édition doit être bloquée.
 * - Échec (coupure réseau…) : les réponses restent dans le localStorage et une nouvelle
 *   tentative part dès que le navigateur détecte la reconnexion.
 *
 * Les envois sont mis en file : jamais deux sauvegardes simultanées pour une même copie.
 */
export function useAutoSave<T>({
  data,
  intervalMs = 30000,
  onSave,
  cleTampon,
  onVerrouillage,
  enabled = true,
}: UseAutoSaveOptions<T>) {
  const [derniereSauvegarde, setDerniereSauvegarde] = useState<string | null>(null)
  const [erreurSauvegarde, setErreurSauvegarde] = useState(false)
  const dataRef = useRef(data)
  const onSaveRef = useRef(onSave)
  const onVerrouillageRef = useRef(onVerrouillage)
  const cleTamponRef = useRef(cleTampon)
  const dernierSerialise = useRef<string | null>(null)
  const file = useRef<Promise<ResultatSauvegarde>>(Promise.resolve("ok"))
  useEffect(() => {
    dataRef.current = data
    onSaveRef.current = onSave
    onVerrouillageRef.current = onVerrouillage
    cleTamponRef.current = cleTampon
  })

  const tenter = useCallback(async (): Promise<ResultatSauvegarde> => {
    const serialise = JSON.stringify(dataRef.current)
    if (serialise === dernierSerialise.current) return "ok"

    try {
      const dateServeur = await onSaveRef.current?.(dataRef.current)
      dernierSerialise.current = serialise
      if (cleTamponRef.current) viderTampon(cleTamponRef.current)
      setErreurSauvegarde(false)
      setDerniereSauvegarde(formatHeure(dateServeur || undefined))
      return "ok"
    } catch (err) {
      if (estCopieVerrouillee(err)) {
        onVerrouillageRef.current?.()
        return "verrouillee"
      }
      if (cleTamponRef.current) ecrireTampon(cleTamponRef.current, serialise)
      setErreurSauvegarde(true)
      return "echec"
    }
  }, [])

  /** Sauvegarde immédiate (ex. avant la soumission), placée dans la file des envois. */
  const sauvegarderMaintenant = useCallback(() => {
    file.current = file.current.then(tenter)
    return file.current
  }, [tenter])

  /** Déclare des données comme déjà présentes sur le serveur (état initial de la copie). */
  const marquerSauvegarde = useCallback((donnees: T) => {
    dernierSerialise.current = JSON.stringify(donnees)
  }, [])

  useEffect(() => {
    if (!enabled) return

    const autoSave = setInterval(sauvegarderMaintenant, intervalMs)
    // Nouvelle tentative automatique dès reconnexion détectée
    window.addEventListener("online", sauvegarderMaintenant)

    return () => {
      clearInterval(autoSave)
      window.removeEventListener("online", sauvegarderMaintenant)
    }
  }, [enabled, intervalMs, sauvegarderMaintenant])

  return {
    derniereSauvegarde,
    erreurSauvegarde,
    sauvegarderMaintenant,
    marquerSauvegarde,
  }
}
