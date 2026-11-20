import { api } from "@/lib/axios"
import type { StatutEvaluation, TypeQuestion } from "@/types"

// Appels de l'espace enseignant sur les évaluations : DS-03 (création, modification), DS-04 (publication),
// DS-06 (tiers-temps) et DS-07 (publication des résultats). Les erreurs du serveur (RFC 7807) se lisent avec
// `messageErreur` de lib/passation.

export interface PromotionApi {
  id: number
  nom: string
  annee: string
  nbEtudiants: number
}

export interface ChoixApi {
  texte: string
  estCorrect: boolean
}

/** Entrée facultative (absente = le programme ne lit rien) ; sortie attendue obligatoire et non vide. */
export interface CasTestApi {
  entree: string | null
  sortieAttendue: string
  estMasque: boolean
}

/** Question vue par son enseignant : contient les bonnes réponses, jamais affichée à un étudiant. */
export interface QuestionApi {
  id: number
  type: TypeQuestion
  enonce: string
  ordre: number
  points: number
  estChoixMultiple: boolean | null
  dureeQuestion: number | null
  /** QCM et quiz : points retirés pour une mauvaise réponse (0 = pas de barème négatif) ; null pour les autres types. */
  penalite?: number | null
  choix: ChoixApi[]
  casTests: CasTestApi[]
}

export interface AmenagementApi {
  etudiantId: number
  dureeMinutes: number
}

export interface EvaluationEnseignantApi {
  id: number
  titre: string
  description: string | null
  dureeStandard: number
  dateOuverture: string
  dateFermeture: string
  statut: StatutEvaluation
  resultatsPublies: boolean
  /** Verrou optimiste : incrémenté à chaque modification côté serveur. */
  version: number
  promotionIds: number[]
  nbQuestions: number
  /** Copies déjà créées par les étudiants (liste seulement). */
  nbCopies: number | null
  /** Étudiants des promotions concernées (liste seulement). */
  nbAttendus: number | null
  /** Vide dans la liste : seul le détail contient les questions. */
  questions: QuestionApi[]
  amenagements: AmenagementApi[]
}

export interface QuestionRequete {
  type: TypeQuestion
  enonce: string
  points: number
  estChoixMultiple?: boolean
  dureeQuestion?: number
  penalite?: number
  choix?: ChoixApi[]
  casTests?: CasTestApi[]
}

/** Corps du POST (création) et du PUT (modification) : l'enseignant vient du jeton, jamais du corps. */
export interface EvaluationRequete {
  titre: string
  description: string
  dureeStandard: number
  dateOuverture: string
  dateFermeture: string
  promotionIds: number[]
  questions: QuestionRequete[]
}

export interface ResultatsPublicationApi {
  evaluationId: number
  resultatsPublies: boolean
  nbCopies: number
  nbCopiesNotees: number
  nbCopiesEnAttente: number
}

const BASE = "/enseignant/evaluations"

export const CLE_PROMOTIONS = ["promotions"] as const
export const CLE_EVALUATIONS_ENSEIGNANT = ["enseignant-evaluations"] as const
export const cleEvaluationEnseignant = (id: string | number) => ["enseignant-evaluation", String(id)] as const

/** DS-03 : promotions proposées à l'enseignant (année la plus récente d'abord). */
export async function listerPromotions() {
  const { data } = await api.get<PromotionApi[]>("/promotions")
  return data
}

/** Évaluations de l'enseignant connecté, sans les questions. */
export async function mesEvaluationsEnseignant() {
  const { data } = await api.get<EvaluationEnseignantApi[]>(BASE)
  return data
}

/** Détail avec les questions et les bonnes réponses ; 403 si l'évaluation est celle d'un autre enseignant. */
export async function detailEvaluationEnseignant(id: string | number) {
  const { data } = await api.get<EvaluationEnseignantApi>(`${BASE}/${id}`)
  return data
}

/** DS-03 : crée un brouillon avec ses questions. */
export async function creerEvaluation(requete: EvaluationRequete) {
  const { data } = await api.post<EvaluationEnseignantApi>(BASE, requete)
  return data
}

/** DS-03 : modifie un brouillon (les questions sont remplacées en bloc). 409 si l'évaluation n'est plus un brouillon. */
export async function modifierEvaluation(id: number, requete: EvaluationRequete) {
  const { data } = await api.put<EvaluationEnseignantApi>(`${BASE}/${id}`, requete)
  return data
}

export async function supprimerEvaluation(id: number) {
  await api.delete(`${BASE}/${id}`)
}

/** DS-04 : BROUILLON → PUBLIEE. */
export async function publierEvaluation(id: number) {
  const { data } = await api.post<EvaluationEnseignantApi>(`${BASE}/${id}/publier`)
  return data
}

/** DS-04 : PUBLIEE → BROUILLON, tant qu'aucune copie n'existe. */
export async function depublierEvaluation(id: number) {
  const { data } = await api.post<EvaluationEnseignantApi>(`${BASE}/${id}/depublier`)
  return data
}

/** DS-07 : rend les notes et corrigés consultables et notifie les étudiants. */
export async function publierResultats(id: number) {
  const { data } = await api.post<ResultatsPublicationApi>(`${BASE}/${id}/resultats/publier`)
  return data
}

/** DS-06 : tiers-temps d'un étudiant. */
export async function definirAmenagement(evaluationId: number, etudiantId: number, dureeMinutes: number) {
  const { data } = await api.put<AmenagementApi>(`${BASE}/${evaluationId}/amenagements/${etudiantId}`, { dureeMinutes })
  return data
}

export async function supprimerAmenagement(evaluationId: number, etudiantId: number) {
  await api.delete(`${BASE}/${evaluationId}/amenagements/${etudiantId}`)
}

export interface EtudiantAmenageApi {
  etudiantId: number
  nom: string
  prenom: string
  numEtudiant: string | null
  /** Absente sans tiers-temps : l'étudiant a alors la durée standard. */
  dureeMinutes: number | null
  /** Faux si l'évaluation est terminée ou si l'étudiant a déjà commencé : le serveur refuserait la modification. */
  modifiable: boolean
}

export interface AmenagementsEvaluationApi {
  evaluationId: number
  dureeStandard: number
  /** Durée de la plage d'ouverture : borne maximale d'un tiers-temps. */
  plageMinutes: number
  etudiants: EtudiantAmenageApi[]
}

export const cleAmenagements = (id: string | number) => ["enseignant-amenagements", String(id)] as const

/** DS-06 : étudiants attendus et tiers-temps de chacun. */
export async function listerAmenagements(evaluationId: string | number) {
  const { data } = await api.get<AmenagementsEvaluationApi>(`${BASE}/${evaluationId}/amenagements`)
  return data
}

/** Message d'erreur d'une durée saisie, ou null si elle sera proposée au serveur. */
export function erreurDuree(saisie: string, dureeStandard: number, plageMinutes: number): string | null {
  const duree = Number(saisie)
  if (saisie.trim() === "" || !Number.isInteger(duree)) return "Saisissez une durée en minutes (nombre entier)."
  if (duree < dureeStandard) return `Un tiers-temps ne peut qu'augmenter la durée (minimum : ${dureeStandard} min).`
  if (duree > plageMinutes) return `La plage d'ouverture ne dure que ${plageMinutes} min : réduisez le tiers-temps ou allongez la fermeture.`
  return null
}

export function libelleNbQuestions(nb: number) {
  return `${nb} question${nb > 1 ? "s" : ""}`
}

/**
 * Actions proposées à l'enseignant sur une ligne de « Mes évaluations », selon l'état de l'évaluation. Le serveur
 * reste juge (409 sinon) : ces règles évitent de proposer une action qui sera refusée.
 */
export interface ActionsEvaluation {
  modifier: boolean
  supprimer: boolean
  publier: boolean
  /** Repasser en brouillon : seulement tant qu'aucun étudiant n'a commencé (DS-04). */
  depublier: boolean
  supervision: boolean
  statistiques: boolean
  /** Résultats publiables : évaluation terminée dont les résultats ne sont pas encore publiés (DS-07). */
  publierResultats: boolean
  /** Tiers-temps (DS-06) : tant que l'évaluation n'est pas terminée. */
  tiersTemps: boolean
  /** Dupliquer en un nouveau brouillon : toujours possible (DS-03). */
  dupliquer: boolean
}

export function actionsDisponibles(e: Pick<EvaluationEnseignantApi, "statut" | "nbCopies" | "resultatsPublies">): ActionsEvaluation {
  const brouillon = e.statut === "BROUILLON"
  return {
    modifier: brouillon,
    supprimer: brouillon,
    publier: brouillon,
    depublier: e.statut === "PUBLIEE" && (e.nbCopies ?? 0) === 0,
    supervision: e.statut === "EN_COURS",
    statistiques: e.statut === "TERMINEE" || e.statut === "PUBLIEE" || e.statut === "EN_COURS",
    publierResultats: e.statut === "TERMINEE" && !e.resultatsPublies,
    tiersTemps: e.statut !== "TERMINEE",
    dupliquer: true,
  }
}
