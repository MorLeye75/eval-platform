import { useState, useEffect, useRef, useCallback } from "react"
import { estCopieVerrouillee, estVersionObsolete, formatHeure } from "@/lib/passation"

export type ResultatSauvegarde = "ok" | "verrouillee" | "conflit" | "echec"

/** Réponse du serveur à une sauvegarde réussie. */
export interface SauvegardeConfirmee {
  dateSauvegarde?: string // ISO 8601, affichée à l'étudiant
  version?: number // nouvelle version des réponses côté serveur
}

interface UseAutoSaveOptions<T> {
  data: T
  intervalMs?: number
  /**
   * Envoie les données au serveur avec la version sur laquelle elles ont été saisies.
   * Résout avec la confirmation du serveur, rejette en cas d'échec.
   */
  onSave?: (data: T, versionAttendue: number | null) => Promise<SauvegardeConfirmee | void>
  /**
   * Clé localStorage du tampon conservé tant que le serveur n'a pas confirmé la sauvegarde.
   * Le tampon porte la version serveur des réponses sur laquelle il a été saisi.
   */
  cleTampon?: string
  /** Le serveur refuse la sauvegarde : temps expiré ou copie qui n'est plus EN_COURS (409). */
  onVerrouillage?: () => void
  /** Le serveur refuse la sauvegarde : copie enregistrée entre-temps ailleurs (409 VERSION_OBSOLETE). */
  onConflit?: () => void
  enabled?: boolean
}

interface Tampon<T> {
  version: number | null
  donnees: T
}

/** Clé où un tampon en conflit avec le serveur est mis de côté jusqu'à la remise de la copie. */
const cleConflit = (cle: string) => `${cle}-conflit`

/**
 * Réponses du tampon, seulement s'il a été saisi sur la version actuelle du serveur.
 * Si le serveur a reçu une sauvegarde plus récente (autre navigateur), les réponses du serveur font foi :
 * le tampon n'est pas restauré mais mis de côté, pour ne pas détruire un travail jamais synchronisé.
 */
export function lireTampon<T>(cle: string, versionServeur: number): T | null {
  try {
    const brut = localStorage.getItem(cle)
    if (!brut) return null
    const tampon = JSON.parse(brut) as Tampon<T>
    if (tampon.version === versionServeur) return tampon.donnees
    localStorage.setItem(cleConflit(cle), brut)
    localStorage.removeItem(cle)
    return null
  } catch {
    return null
  }
}

function retirer(cle: string) {
  try {
    localStorage.removeItem(cle)
  } catch {
    // stockage indisponible (navigation privée) : rien à vider
  }
}

/** Supprime le tampon et un éventuel tampon en conflit (copie remise). */
export function viderTampon(cle: string) {
  retirer(cle)
  retirer(cleConflit(cle))
}

/** Met des réponses de côté sans les restaurer : elles ne seront pas perdues avant la remise. */
function mettreDeCote<T>(cle: string, tampon: Tampon<T>) {
  ecrireTampon(cleConflit(cle), tampon)
  retirer(cle)
}

function ecrireTampon<T>(cle: string, tampon: Tampon<T>) {
  try {
    localStorage.setItem(cle, JSON.stringify(tampon))
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
 * - 409 VERSION_OBSOLETE (copie enregistrée depuis un autre onglet ou appareil) : les réponses locales
 *   sont mises de côté et `onConflit` doit recharger la copie du serveur.
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
  onConflit,
  enabled = true,
}: UseAutoSaveOptions<T>) {
  const [derniereSauvegarde, setDerniereSauvegarde] = useState<string | null>(null)
  const [erreurSauvegarde, setErreurSauvegarde] = useState(false)
  const dataRef = useRef(data)
  const onSaveRef = useRef(onSave)
  const onVerrouillageRef = useRef(onVerrouillage)
  const onConflitRef = useRef(onConflit)
  const cleTamponRef = useRef(cleTampon)
  const dernierSerialise = useRef<string | null>(null)
  // Version serveur des réponses : celle de la dernière sauvegarde confirmée
  const versionServeur = useRef<number | null>(null)
  const file = useRef<Promise<ResultatSauvegarde>>(Promise.resolve("ok"))
  useEffect(() => {
    dataRef.current = data
    onSaveRef.current = onSave
    onVerrouillageRef.current = onVerrouillage
    onConflitRef.current = onConflit
    cleTamponRef.current = cleTampon
  })

  const tenter = useCallback(async (): Promise<ResultatSauvegarde> => {
    const donnees = dataRef.current
    const serialise = JSON.stringify(donnees)
    if (serialise === dernierSerialise.current) return "ok"

    try {
      const confirmation = await onSaveRef.current?.(donnees, versionServeur.current)
      dernierSerialise.current = serialise
      if (confirmation?.version !== undefined) versionServeur.current = confirmation.version
      // Tampon synchronisé : seul le tampon courant est vidé, un tampon en conflit reste de côté
      if (cleTamponRef.current) retirer(cleTamponRef.current)
      setErreurSauvegarde(false)
      setDerniereSauvegarde(formatHeure(confirmation?.dateSauvegarde))
      return "ok"
    } catch (err) {
      if (estCopieVerrouillee(err)) {
        onVerrouillageRef.current?.()
        return "verrouillee"
      }
      if (estVersionObsolete(err)) {
        if (cleTamponRef.current) {
          mettreDeCote(cleTamponRef.current, { version: versionServeur.current, donnees })
        }
        onConflitRef.current?.()
        return "conflit"
      }
      if (cleTamponRef.current) {
        ecrireTampon(cleTamponRef.current, { version: versionServeur.current, donnees })
      }
      setErreurSauvegarde(true)
      return "echec"
    }
  }, [])

  /** Sauvegarde immédiate (ex. avant la soumission), placée dans la file des envois. */
  const sauvegarderMaintenant = useCallback(() => {
    file.current = file.current.then(tenter)
    return file.current
  }, [tenter])

  /** Déclare des données comme déjà présentes sur le serveur, dans la version indiquée (état initial de la copie). */
  const marquerSauvegarde = useCallback((donnees: T, version: number) => {
    dernierSerialise.current = JSON.stringify(donnees)
    versionServeur.current = version
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
