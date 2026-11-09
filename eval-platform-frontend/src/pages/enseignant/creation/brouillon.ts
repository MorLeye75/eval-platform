import { useEffect, useRef, useState } from "react"
import { useLocation, useNavigate, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { isAxiosError } from "axios"
import type { StatutEvaluation, TypeQuestion } from "@/types"
import { CLE_BROUILLON_EVALUATION } from "@/lib/session"
import { cleEvaluationEnseignant, detailEvaluationEnseignant } from "@/lib/evaluationsEnseignant"
import { versBrouillon } from "./apiBrouillon"

// ============================================================
// Brouillon partagé par les 3 étapes de l'assistant de création / édition.
// Il est conservé en sessionStorage : revenir à une étape précédente
// (bouton « Retour » ou navigateur) ou recharger la page ne perd rien.
// Il est effacé à l'enregistrement final ou à l'annulation.
// ============================================================

export interface InfosGeneralesForm {
  titre: string
  description: string
  duree: number          // minutes
  dateOuverture: string  // format datetime-local (AAAA-MM-JJTHH:mm)
  dateFermeture: string  // format datetime-local (AAAA-MM-JJTHH:mm)
  statut: StatutEvaluation
  promotionIds: number[] // au moins une (GET /promotions)
}

export interface ChoixLocal {
  id: number
  texte: string
  estCorrect: boolean
}

export interface CasTestLocal {
  id: number
  entree: string
  sortieAttendue: string
  estMasque: boolean
}

export interface QuestionLocal {
  localId: number
  type: TypeQuestion
  enonce: string
  points: number
  // QCM et QUIZ_CHRONOMETREE
  estChoixMultiple?: boolean
  choix?: ChoixLocal[]
  // CODE
  casTests?: CasTestLocal[]
  // QUIZ_CHRONOMETREE
  dureeQuestion?: number  // secondes
}

export interface BrouillonEvaluation {
  evaluationId: number | null   // null = création
  infos: InfosGeneralesForm
  questions: QuestionLocal[]
}

export const INFOS_VIDES: InfosGeneralesForm = {
  titre: "",
  description: "",
  duree: 60,
  dateOuverture: "",
  dateFermeture: "",
  statut: "BROUILLON",
  promotionIds: [],
}

// Effacé à la déconnexion et sur jeton refusé (voir lib/session)
const CLE_STOCKAGE = CLE_BROUILLON_EVALUATION

// ──────────────────────────────────────────────────────────
//  Identifiants locaux (questions, choix, cas de test)
// ──────────────────────────────────────────────────────────

let dernierId = 0
export function nouvelId() {
  dernierId += 1
  return dernierId
}

/** Évite qu'un brouillon restauré n'entre en collision avec les nouveaux ids. */
function reserverIds(questions: QuestionLocal[]) {
  for (const q of questions) {
    dernierId = Math.max(
      dernierId,
      q.localId,
      ...(q.choix ?? []).map((c) => c.id),
      ...(q.casTests ?? []).map((ct) => ct.id),
    )
  }
}

// ──────────────────────────────────────────────────────────
//  Validation
// ──────────────────────────────────────────────────────────

export function validerInfos(form: InfosGeneralesForm) {
  const erreurs: Partial<Record<keyof InfosGeneralesForm, string>> = {}
  if (!form.titre.trim())       erreurs.titre         = "Le titre est obligatoire."
  if (!form.description.trim()) erreurs.description   = "La description est obligatoire."
  if (form.duree < 5)           erreurs.duree         = "La durée doit être d'au moins 5 minutes."
  if (!form.dateOuverture)      erreurs.dateOuverture = "La date d'ouverture est obligatoire."
  if (!form.dateFermeture)      erreurs.dateFermeture = "La date de fermeture est obligatoire."
  if (form.dateOuverture && form.dateFermeture && form.dateFermeture <= form.dateOuverture)
    erreurs.dateFermeture = "La fermeture doit être après l'ouverture."
  if (form.promotionIds.length === 0) erreurs.promotionIds = "Choisissez au moins une promotion."
  // La fin d'une épreuve est plafonnée à la fermeture : sans plage assez longue, la publication serait refusée
  if (form.statut === "PUBLIEE" && form.dateOuverture && form.dateFermeture && form.dateFermeture > form.dateOuverture) {
    const plage = Math.round((new Date(form.dateFermeture).getTime() - new Date(form.dateOuverture).getTime()) / 60_000)
    if (form.duree > plage)
      erreurs.dateFermeture = `La plage d'ouverture (${plage} min) est plus courte que la durée (${form.duree} min) : allongez la fermeture.`
  }
  return erreurs
}

/** Première erreur bloquante d'une question, ou null si elle est complète. */
/**
 * Bascule choix unique / multiple. En repassant en choix unique, seule la première bonne réponse reste cochée :
 * sinon la question aurait plusieurs bonnes réponses pour un seul choix possible, et personne ne la réussirait.
 */
export function basculerChoixMultiple(q: QuestionLocal, multiple: boolean): Partial<QuestionLocal> {
  if (multiple) return { estChoixMultiple: true }
  let bonneReponseGardee = false
  const choix = (q.choix ?? []).map((c) => {
    if (!c.estCorrect) return c
    if (bonneReponseGardee) return { ...c, estCorrect: false }
    bonneReponseGardee = true
    return c
  })
  return { estChoixMultiple: false, choix }
}

export function erreurQuestion(q: QuestionLocal): string | null {
  if (!q.enonce.trim()) return "Énoncé manquant."
  if (!(q.points > 0)) return "Le barème doit être supérieur à 0."
  if (q.type === "QCM" || q.type === "QUIZ_CHRONOMETREE") {
    const choix = q.choix ?? []
    if (choix.length < 2) return "Au moins 2 choix sont nécessaires."
    if (choix.some((c) => !c.texte.trim())) return "Un des choix est vide."
    if (!choix.some((c) => c.estCorrect)) return "Cochez au moins une bonne réponse."
    if (!q.estChoixMultiple && choix.filter((c) => c.estCorrect).length > 1)
      return "Un choix unique ne peut avoir qu'une bonne réponse."
  }
  if (q.type === "QUIZ_CHRONOMETREE" && !((q.dureeQuestion ?? 0) >= 5))
    return "La durée de la question doit être d'au moins 5 secondes."
  if (q.type === "CODE") {
    const casTests = q.casTests ?? []
    if (casTests.length === 0) return "Ajoutez au moins un cas de test."
    // Colonne obligatoire côté serveur, et sans elle le cas ne peut pas être corrigé
    const sansSortie = casTests.findIndex((c) => !c.sortieAttendue.trim())
    if (sansSortie >= 0) return `Le cas de test ${sansSortie + 1} n'a pas de sortie attendue.`
  }
  return null
}

// ──────────────────────────────────────────────────────────
//  Persistance
// ──────────────────────────────────────────────────────────

function lireBrouillon(): BrouillonEvaluation | null {
  try {
    const brut = sessionStorage.getItem(CLE_STOCKAGE)
    if (!brut) return null
    const brouillon = JSON.parse(brut) as BrouillonEvaluation
    // Un brouillon gardé avant l'ajout du choix des promotions n'a pas ce champ
    return { ...brouillon, infos: { ...brouillon.infos, promotionIds: brouillon.infos.promotionIds ?? [] } }
  } catch {
    return null
  }
}

export function effacerBrouillon() {
  try {
    sessionStorage.removeItem(CLE_STOCKAGE)
  } catch {
    // stockage indisponible : rien à effacer
  }
}

/** Brouillon vide d'une création ; en édition, il est remplacé par le détail de l'évaluation renvoyé par l'API. */
function brouillonInitial(evaluationId: number | null): BrouillonEvaluation {
  return { evaluationId, infos: INFOS_VIDES, questions: [] }
}

// ──────────────────────────────────────────────────────────
//  Hook
// ──────────────────────────────────────────────────────────

export type EtapeCreation = "infos" | "questions" | "previsualisation"

/**
 * Brouillon de l'assistant pour la route courante :
 * `/enseignant/evaluations/creation/...` ou `/enseignant/evaluations/:id/edition/...`.
 * Un lien ouvert avec `state={{ nouveau: true }}` repart d'un brouillon vide.
 */
export function useBrouillon() {
  const { id } = useParams<{ id: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const evaluationId = id ? Number(id) : null
  const nouveau = (location.state as { nouveau?: boolean } | null)?.nouveau === true

  // Brouillon gardé sur cet onglet (retour arrière, rechargement) : il passe avant le serveur
  const restaure = useRef<boolean>(false)
  const [brouillon, setBrouillon] = useState<BrouillonEvaluation>(() => {
    const existant = lireBrouillon()
    const garde = !nouveau && existant && existant.evaluationId === evaluationId
    restaure.current = Boolean(garde)
    const b = garde ? existant : brouillonInitial(evaluationId)
    reserverIds(b.questions)
    return b
  })

  // Édition sans brouillon local : les questions viennent du serveur (GET /enseignant/evaluations/{id})
  const [aCharger] = useState(() => evaluationId !== null && !restaure.current)
  const detail = useQuery({
    queryKey: cleEvaluationEnseignant(evaluationId ?? 0),
    queryFn: () => detailEvaluationEnseignant(evaluationId as number),
    enabled: aCharger,
    retry: false,
    refetchOnWindowFocus: false,
    gcTime: 0, // jamais de détail périmé : le brouillon local prend ensuite le relais
  })
  const termine = useRef(false)
  const detailApplique = useRef(false)
  useEffect(() => {
    if (!detail.data || detailApplique.current) return
    detailApplique.current = true
    const charge = versBrouillon(detail.data)
    reserverIds(charge.questions)
    setBrouillon(charge)
  }, [detail.data])

  useEffect(() => {
    // Pendant le chargement, le brouillon vide ne doit pas écraser un éventuel brouillon gardé
    if (termine.current) return // enregistrement confirmé : le brouillon est effacé, il ne doit pas renaître
    if (aCharger && !detailApplique.current) return
    try {
      sessionStorage.setItem(CLE_STOCKAGE, JSON.stringify(brouillon))
    } catch {
      // stockage indisponible : le brouillon ne survivra qu'à l'étape courante
    }
  }, [brouillon, aCharger])

  // Le marqueur « nouveau » ne doit servir qu'une fois (pas à un rechargement)
  useEffect(() => {
    if (nouveau) navigate(location.pathname, { replace: true, state: null })
  }, [nouveau, navigate, location.pathname])

  const base = id ? `/enseignant/evaluations/${id}/edition` : "/enseignant/evaluations/creation"
  function chemin(etape: EtapeCreation) {
    return etape === "infos" ? base : `${base}/${etape}`
  }

  // 404 (inconnue) et 403 (évaluation d'un autre enseignant) : même message, on ne révèle pas son existence
  const refuse = aCharger && detail.isError && isAxiosError(detail.error)
    && (detail.error.response?.status === 404 || detail.error.response?.status === 403)

  /** Enregistrement confirmé par le serveur : efface le brouillon local et empêche toute réécriture. */
  function terminer() {
    termine.current = true
    effacerBrouillon()
  }

  return {
    brouillon,
    setBrouillon,
    terminer,
    estEdition: evaluationId !== null,
    /** Détail de l'évaluation en cours de chargement depuis le serveur. */
    chargement: aCharger && detail.isPending,
    evaluationIntrouvable: Boolean(refuse),
    /** Erreur autre que 404 / 403 (serveur injoignable, 500...). */
    erreurChargement: aCharger && detail.isError && !refuse,
    /** Seul un brouillon est modifiable (409 sinon) : le dire avant que l'enseignant ne retape tout. */
    nonModifiable: detail.data !== undefined && detail.data.statut !== "BROUILLON",
    chemin,
  }
}
