import { useState, useEffect, useRef, useCallback } from "react"
import {
  estCopieVerrouillee,
  estErreurReseau,
  estRequeteRefusee,
  estVersionObsolete,
  formatHeure,
  messageErreur,
} from "@/lib/passation"

export type ResultatSauvegarde = "ok" | "verrouillee" | "conflit" | "refusee" | "echec"

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
  /**
   * Après un 409 VERSION_OBSOLETE qui suit des envois restés sans réponse (délai dépassé, coupure) : le serveur
   * contient-il l'un de ces envois ? Résout avec sa version et l'indice de l'envoi trouvé, sinon null.
   * Une sauvegarde enregistrée dont la réponse s'est perdue n'est alors pas prise pour un conflit.
   */
  verifierEnvoiPerdu?: (envois: T[]) => Promise<{ version: number; index: number } | null>
  enabled?: boolean
}

/** Envois sans réponse retenus au plus : chacun a pu être enregistré par le serveur. */
const ENVOIS_INCERTAINS_MAX = 3

interface Tampon<T> {
  version: number | null
  donnees: T
  /** Envois restés sans réponse depuis la dernière sauvegarde confirmée : le serveur a pu les enregistrer. */
  envois?: T[]
}

/** Clé où un tampon en conflit avec le serveur est mis de côté jusqu'à la remise de la copie. */
const cleConflit = (cle: string) => `${cle}-conflit`

/**
 * Réponses du tampon, s'il a été saisi sur la version actuelle du serveur, ou si le serveur contient l'un de ses
 * envois restés sans réponse (`estNotreEnvoi`) : la version plus récente du serveur vient alors de ce navigateur
 * (sauvegarde enregistrée dont la réponse s'est perdue) et les réponses tapées depuis ne doivent pas disparaître.
 * Sinon le serveur a reçu une sauvegarde d'ailleurs (autre navigateur) et ses réponses font foi :
 * le tampon n'est pas restauré mais mis de côté, pour ne pas détruire un travail jamais synchronisé.
 */
export function lireTampon<T>(cle: string, versionServeur: number, estNotreEnvoi?: (envoi: T) => boolean): T | null {
  try {
    const brut = localStorage.getItem(cle)
    if (!brut) return null
    const tampon = JSON.parse(brut) as Tampon<T>
    if (tampon.version === versionServeur) return tampon.donnees
    if (estNotreEnvoi && tampon.envois?.some(estNotreEnvoi)) return tampon.donnees
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
 * - Chaque modification est écrite aussitôt dans le tampon localStorage : un rechargement de la page
 *   entre deux envois ne perd rien. Fermer ou recharger la page avec des réponses non envoyées
 *   demande confirmation.
 * - Toutes les 30 secondes (par défaut), envoie les données si elles ont changé.
 * - Succès : affiche l'heure de sauvegarde renvoyée par le serveur et vide le tampon, sauf si
 *   l'étudiant a modifié ses réponses pendant l'envoi (le tampon passe alors sur la nouvelle version).
 * - 409 (temps expiré ou copie verrouillée) : `onVerrouillage`, l'édition doit être bloquée.
 * - 409 VERSION_OBSOLETE (copie enregistrée depuis un autre onglet ou appareil) : les réponses locales
 *   sont mises de côté et `onConflit` doit recharger la copie du serveur.
 * - 400 et autres refus du contenu : `refusSauvegarde` porte le message du serveur, les réponses restent dans le
 *   tampon et ce même contenu n'est pas renvoyé (il serait refusé de nouveau) ; une modification repart normalement.
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
  verifierEnvoiPerdu,
  enabled = true,
}: UseAutoSaveOptions<T>) {
  const [derniereSauvegarde, setDerniereSauvegarde] = useState<string | null>(null)
  const [erreurSauvegarde, setErreurSauvegarde] = useState(false)
  const [refusSauvegarde, setRefusSauvegarde] = useState<string | null>(null)
  // Dernier contenu refusé par le serveur : inutile de le renvoyer tel quel
  const dernierRefuse = useRef<string | null>(null)
  const dataRef = useRef(data)
  const onSaveRef = useRef(onSave)
  const onVerrouillageRef = useRef(onVerrouillage)
  const onConflitRef = useRef(onConflit)
  const verifierEnvoiPerduRef = useRef(verifierEnvoiPerdu)
  // Envois restés sans réponse depuis la dernière sauvegarde confirmée (le serveur a pu les enregistrer)
  const envoisIncertains = useRef<{ donnees: T; serialise: string }[]>([])
  const cleTamponRef = useRef(cleTampon)
  const dernierSerialise = useRef<string | null>(null)
  // Version serveur des réponses : celle de la dernière sauvegarde confirmée
  const versionServeur = useRef<number | null>(null)
  const file = useRef<Promise<ResultatSauvegarde>>(Promise.resolve("ok"))
  /** Tampon des réponses données, avec les envois restés sans réponse (relus après un rechargement de la page). */
  const tamponCourant = useCallback((donnees: T): Tampon<T> => {
    const envois = envoisIncertains.current.map((e) => e.donnees)
    return { version: versionServeur.current, donnees, ...(envois.length > 0 ? { envois } : {}) }
  }, [])
  useEffect(() => {
    dataRef.current = data
    onSaveRef.current = onSave
    onVerrouillageRef.current = onVerrouillage
    onConflitRef.current = onConflit
    verifierEnvoiPerduRef.current = verifierEnvoiPerdu
    cleTamponRef.current = cleTampon
  })

  const tenter = useCallback(async function envoyer(): Promise<ResultatSauvegarde> {
    const donnees = dataRef.current
    const serialise = JSON.stringify(donnees)
    if (serialise === dernierSerialise.current) return "ok"
    if (serialise === dernierRefuse.current) return "refusee"

    try {
      const confirmation = await onSaveRef.current?.(donnees, versionServeur.current)
      dernierSerialise.current = serialise
      envoisIncertains.current = []
      if (confirmation?.version !== undefined) versionServeur.current = confirmation.version
      const cle = cleTamponRef.current
      if (cle) {
        // Seul le tampon courant est concerné, un tampon en conflit reste de côté
        const actuelles = dataRef.current
        if (JSON.stringify(actuelles) === serialise) retirer(cle)
        // Modifié pendant l'envoi : ces réponses restent à envoyer, sur la version que le serveur vient de donner
        else ecrireTampon(cle, tamponCourant(actuelles))
      }
      dernierRefuse.current = null
      setRefusSauvegarde(null)
      setErreurSauvegarde(false)
      setDerniereSauvegarde(formatHeure(confirmation?.dateSauvegarde))
      return "ok"
    } catch (err) {
      if (estCopieVerrouillee(err)) {
        onVerrouillageRef.current?.()
        return "verrouillee"
      }
      // Les réponses les plus récentes (celles tapées pendant l'envoi comprises) sont gardées, pas celles envoyées
      if (estVersionObsolete(err)) {
        // Notre propre sauvegarde, enregistrée mais dont la réponse s'est perdue ? Alors pas de conflit :
        // on adopte la version du serveur et les réponses actuelles repartent aussitôt
        const incertains = envoisIncertains.current
        envoisIncertains.current = []
        if (incertains.length > 0 && verifierEnvoiPerduRef.current) {
          try {
            const trouve = await verifierEnvoiPerduRef.current(incertains.map((e) => e.donnees))
            if (trouve) {
              versionServeur.current = trouve.version
              dernierSerialise.current = incertains[trouve.index].serialise
              return envoyer()
            }
          } catch {
            // Vérification impossible : traité comme un vrai conflit
          }
        }
        if (cleTamponRef.current) {
          mettreDeCote(cleTamponRef.current, { version: versionServeur.current, donnees: dataRef.current })
        }
        onConflitRef.current?.()
        return "conflit"
      }
      // Contenu refusé (400…) : ni coupure ni panne, le renvoyer tel quel échouerait encore
      if (estRequeteRefusee(err)) {
        dernierRefuse.current = serialise
        if (cleTamponRef.current) ecrireTampon(cleTamponRef.current, tamponCourant(dataRef.current))
        setErreurSauvegarde(false)
        setRefusSauvegarde(messageErreur(err, "Vos réponses ont été refusées par le serveur."))
        return "refusee"
      }
      // Sans réponse (délai dépassé, coupure) : le serveur a peut-être enregistré cet envoi
      if (estErreurReseau(err)) {
        envoisIncertains.current = [...envoisIncertains.current, { donnees, serialise }].slice(-ENVOIS_INCERTAINS_MAX)
      }
      if (cleTamponRef.current) {
        ecrireTampon(cleTamponRef.current, tamponCourant(dataRef.current))
      }
      setErreurSauvegarde(true)
      return "echec"
    }
  }, [tamponCourant])

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

  // Chaque modification non envoyée part aussitôt dans le tampon (rechargement de page entre deux envois)
  useEffect(() => {
    if (!enabled || !cleTampon) return
    // Retour aux réponses du serveur (modification annulée) : plus rien à garder, sinon un F5 la ressusciterait
    if (JSON.stringify(data) === dernierSerialise.current) {
      retirer(cleTampon)
      return
    }
    ecrireTampon(cleTampon, tamponCourant(data))
  }, [data, enabled, cleTampon, tamponCourant])

  // Fermeture ou rechargement avec des réponses non envoyées : le navigateur demande confirmation
  useEffect(() => {
    if (!enabled) return
    const avantFermeture = (event: BeforeUnloadEvent) => {
      if (JSON.stringify(dataRef.current) === dernierSerialise.current) return
      event.preventDefault()
    }
    window.addEventListener("beforeunload", avantFermeture)
    return () => window.removeEventListener("beforeunload", avantFermeture)
  }, [enabled])

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
    refusSauvegarde,
    sauvegarderMaintenant,
    marquerSauvegarde,
  }
}
