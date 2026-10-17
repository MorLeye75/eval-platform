import { useEffect, useState } from "react"
import { useLocation, useNavigate, useParams } from "react-router-dom"
import type { StatutEvaluation, TypeQuestion } from "@/types"
import { getEvaluationEnseignant } from "@/mocks/evaluationsEnseignant"

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
}

const CLE_STOCKAGE = "brouillon-evaluation"

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
  return erreurs
}

/** Première erreur bloquante d'une question, ou null si elle est complète. */
export function erreurQuestion(q: QuestionLocal): string | null {
  if (!q.enonce.trim()) return "Énoncé manquant."
  if (!(q.points > 0)) return "Le barème doit être supérieur à 0."
  if (q.type === "QCM" || q.type === "QUIZ_CHRONOMETREE") {
    const choix = q.choix ?? []
    if (choix.length < 2) return "Au moins 2 choix sont nécessaires."
    if (choix.some((c) => !c.texte.trim())) return "Un des choix est vide."
    if (!choix.some((c) => c.estCorrect)) return "Cochez au moins une bonne réponse."
  }
  if (q.type === "QUIZ_CHRONOMETREE" && !((q.dureeQuestion ?? 0) >= 5))
    return "La durée de la question doit être d'au moins 5 secondes."
  if (q.type === "CODE" && (q.casTests ?? []).length === 0)
    return "Ajoutez au moins un cas de test."
  return null
}

// ──────────────────────────────────────────────────────────
//  Persistance
// ──────────────────────────────────────────────────────────

function lireBrouillon(): BrouillonEvaluation | null {
  try {
    const brut = sessionStorage.getItem(CLE_STOCKAGE)
    return brut ? (JSON.parse(brut) as BrouillonEvaluation) : null
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

/** ISO 8601 → valeur acceptée par un <input type="datetime-local"> */
function versDateTimeLocal(iso: string) {
  return iso.slice(0, 16)
}

function brouillonInitial(evaluationId: number | null): BrouillonEvaluation {
  const evaluation = evaluationId !== null ? getEvaluationEnseignant(evaluationId) : undefined
  if (!evaluation) return { evaluationId, infos: INFOS_VIDES, questions: [] }
  // TODO : GET /api/enseignant/evaluations/{id} pour charger aussi les questions
  return {
    evaluationId,
    infos: {
      titre: evaluation.titre,
      description: evaluation.description,
      duree: evaluation.duree,
      dateOuverture: versDateTimeLocal(evaluation.dateOuverture),
      dateFermeture: versDateTimeLocal(evaluation.dateFermeture),
      statut: evaluation.statut === "PUBLIEE" ? "PUBLIEE" : "BROUILLON",
    },
    questions: [],
  }
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

  const [brouillon, setBrouillon] = useState<BrouillonEvaluation>(() => {
    const existant = lireBrouillon()
    const b = !nouveau && existant && existant.evaluationId === evaluationId
      ? existant
      : brouillonInitial(evaluationId)
    reserverIds(b.questions)
    return b
  })

  useEffect(() => {
    try {
      sessionStorage.setItem(CLE_STOCKAGE, JSON.stringify(brouillon))
    } catch {
      // stockage indisponible : le brouillon ne survivra qu'à l'étape courante
    }
  }, [brouillon])

  // Le marqueur « nouveau » ne doit servir qu'une fois (pas à un rechargement)
  useEffect(() => {
    if (nouveau) navigate(location.pathname, { replace: true, state: null })
  }, [nouveau, navigate, location.pathname])

  const base = id ? `/enseignant/evaluations/${id}/edition` : "/enseignant/evaluations/creation"
  function chemin(etape: EtapeCreation) {
    return etape === "infos" ? base : `${base}/${etape}`
  }

  return {
    brouillon,
    setBrouillon,
    estEdition: evaluationId !== null,
    evaluationIntrouvable: evaluationId !== null && !getEvaluationEnseignant(evaluationId),
    chemin,
  }
}
