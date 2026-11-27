import { api } from "@/lib/axios"
import type { StatutProposition } from "@/types"

// Correction assistée par IA et validation par l'enseignant (DS-08). L'IA propose, l'enseignant décide : la note
// retenue est toujours la sienne. Les erreurs du serveur (RFC 7807) se lisent avec `messageErreur` de lib/passation.

export interface PropositionIAApi {
  id: number
  reponseId: number
  evaluationId: number
  evaluation: string
  etudiant: string
  question: string
  reponse: string | null
  pointsMax: number
  noteProposee: number
  justification: string | null
  statut: StatutProposition
  /** Note retenue par l'enseignant (propositions déjà traitées uniquement). */
  noteRetenue: number | null
}

export interface CorrectionsIAApi {
  propositions: PropositionIAApi[]
  /** Réponses encore sans proposition (génération en cours, ou modèle indisponible). */
  enCours: number
}

export interface NoteApi {
  reponseId: number
  noteRetenue: number
  /** Vrai si c'était la dernière note de la copie : elle est passée EVALUE. */
  copieEvaluee: boolean
  noteFinale: number | null
}

export const CLE_CORRECTIONS_IA = ["enseignant-corrections-ia"] as const

/** DS-08 A : lance la génération (asynchrone) pour une évaluation terminée. */
export async function genererPropositionsIA(evaluationId: number) {
  const { data } = await api.post<{ evaluationId: number; nbReponsesAProposer: number }>(
    `/enseignant/evaluations/${evaluationId}/corrections-ia/generer`
  )
  return data
}

/** DS-08 C : propositions de l'enseignant, avec le nombre de réponses encore sans proposition. */
export async function listerCorrectionsIA() {
  const { data } = await api.get<CorrectionsIAApi>("/enseignant/corrections-ia")
  return data
}

/** DS-08 D : valide ; sans note, la note de l'IA est retenue. */
export async function validerProposition(id: number, note?: number) {
  const { data } = await api.put<NoteApi>(`/enseignant/corrections-ia/${id}/valider`, note === undefined ? {} : { note })
  return data
}

/** DS-08 D : rejette avec la note de l'enseignant (obligatoire). */
export async function rejeterProposition(id: number, note: number) {
  const { data } = await api.put<NoteApi>(`/enseignant/corrections-ia/${id}/rejeter`, { note })
  return data
}

/** DS-08 E : note une réponse sans proposition. */
export async function noterReponse(reponseId: number, note: number) {
  const { data } = await api.put<NoteApi>(`/enseignant/reponses/${reponseId}/note`, { note })
  return data
}

export interface ReponseANoterApi {
  reponseId: number
  evaluationId: number
  evaluation: string
  etudiant: string
  question: string
  reponse: string | null
  pointsMax: number
}

export const CLE_NOTES_MANUELLES = ["enseignant-corrections-manuelles"] as const

/** DS-08 E : réponses ouvertes sans proposition IA et pas encore notées, à noter à la main. */
export async function listerANoterAlaMain() {
  const { data } = await api.get<ReponseANoterApi[]>("/enseignant/corrections-manuelles")
  return data
}

/** Note saisie (virgule ou point) ; null si absente ou hors de [0, barème]. */
export function lireNoteSaisie(saisie: string, pointsMax: number): number | null {
  const texte = saisie.trim().replace(",", ".")
  if (texte === "") return null
  const note = Number(texte)
  return Number.isFinite(note) && note >= 0 && note <= pointsMax ? note : null
}
