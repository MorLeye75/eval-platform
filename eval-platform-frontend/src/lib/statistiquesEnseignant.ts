import { api } from "@/lib/axios"
import type { StatutEvaluation } from "@/types"

// Statistiques et export CSV d'une évaluation (DS-09). Toutes les notes agrégées sont sur 20 ; un indicateur vaut
// null (jamais 0) tant qu'aucune copie n'est notée. Erreurs du serveur : `messageErreur` de lib/passation.

export interface TrancheApi {
  de: number
  a: number
  nb: number
}

export interface QuestionStatApi {
  questionId: number
  ordre: number
  enonce: string
  points: number
  moyennePoints: number | null
  tauxReussite: number | null
}

export interface StatistiquesApi {
  evaluationId: number
  titre: string
  statut: StatutEvaluation
  bareme: number
  nbAttendus: number
  nbCopies: number
  nbNotees: number
  nbEnCorrection: number
  moyenneSur20: number | null
  medianeSur20: number | null
  minSur20: number | null
  maxSur20: number | null
  ecartTypeSur20: number | null
  tauxReussite: number | null
  distribution: TrancheApi[]
  questions: QuestionStatApi[]
}

export const cleStatistiques = (id: string | number) => ["enseignant-statistiques", String(id)] as const

/** Questions en dessous de ce taux : « à reprendre en cours ». */
export const SEUIL_QUESTION_DIFFICILE = 60

export async function statistiquesEvaluation(id: string | number) {
  const { data } = await api.get<StatistiquesApi>(`/enseignant/evaluations/${id}/statistiques`)
  return data
}

/** Fichier CSV des résultats : lu en blob, donné au navigateur pour téléchargement. */
export async function exporterResultatsCsv(id: number) {
  const { data } = await api.get<Blob>(`/enseignant/evaluations/${id}/export/csv`, { responseType: "blob" })
  return data
}

/** Détail par question (une ligne par étudiant et par question) : même principe que le CSV des résultats. */
export async function exporterDetailCsv(id: number) {
  const { data } = await api.get<Blob>(`/enseignant/evaluations/${id}/export/detail-csv`, { responseType: "blob" })
  return data
}

export function telechargerFichier(blob: Blob, nom: string) {
  const url = URL.createObjectURL(blob)
  const lien = document.createElement("a")
  lien.href = url
  lien.download = nom
  lien.click()
  URL.revokeObjectURL(url)
}

/** Note sur 20 pour l'affichage : « — » tant qu'il n'y a aucune copie notée. */
export function formaterSur20(valeur: number | null) {
  return valeur === null ? "—" : `${valeur.toLocaleString("fr-FR")} / 20`
}

/** Questions les plus ratées, de la plus difficile à la moins difficile (au plus 5). */
export function questionsDifficiles(questions: QuestionStatApi[]) {
  return questions
    .filter((q) => q.tauxReussite !== null && q.tauxReussite < SEUIL_QUESTION_DIFFICILE)
    .sort((a, b) => (a.tauxReussite ?? 0) - (b.tauxReussite ?? 0))
    .slice(0, 5)
}

export interface LigneResultatApi {
  etudiantId: number
  numEtudiant: string | null
  nom: string
  prenom: string
  /** ABSENT sans copie, sinon le statut de la copie (EN_COURS, SOUMIS, EN_CORRECTION, EVALUE). */
  etat: string
  /** null (jamais 0) tant que la copie n'est pas notée. */
  note: number | null
  noteSurVingt: number | null
  dateRemise: string | null
}

export interface ResultatsEvaluationApi {
  evaluationId: number
  bareme: number
  lignes: LigneResultatApi[]
}

export const cleResultats = (id: string | number) => ["enseignant-resultats", String(id)] as const

/** DS-09 C : notes par étudiant attendu (mêmes lignes que le CSV, sans e-mail). 409 avant l'ouverture. */
export async function resultatsEvaluation(id: string | number) {
  const { data } = await api.get<ResultatsEvaluationApi>(`/enseignant/evaluations/${id}/resultats`)
  return data
}

const ETATS: Record<string, string> = {
  ABSENT: "Absent",
  EN_COURS: "En cours",
  SOUMIS: "Remise, pas encore corrigée",
  EN_CORRECTION: "En attente",
  EVALUE: "Notée",
}

/** Libellé d'état d'une copie pour l'enseignant. */
export function libelleEtatCopie(etat: string) {
  return ETATS[etat] ?? etat
}
