import { api } from "@/lib/axios"
import type { Performances, ResultatsCopie, VerdictCas } from "@/types"

/** Résultats d'une copie de l'étudiant connecté (DS-13). */
export async function resultatsCopie(soumissionId: string | number) {
  const { data } = await api.get<ResultatsCopie>(`/soumissions/${soumissionId}/resultats`)
  return data
}

/** Tableau de bord de l'étudiant : évaluations notées et publiées, notes sur 20 (DS-13). */
export async function mesPerformances() {
  const { data } = await api.get<Performances>("/etudiant/performances")
  return data
}

export const cleResultats = (soumissionId: string | number) => ["resultats", String(soumissionId)] as const
export const CLE_PERFORMANCES = ["performances"] as const

/** Note à la française : virgule décimale, décimales inutiles supprimées (« 14,5 », « 12 »). */
export function formatNote(note: number) {
  return note.toLocaleString("fr-FR", { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

/** Écart signé : « +2,5 », « -1 », « 0 ». */
export function formatEcart(ecart: number) {
  return `${ecart > 0 ? "+" : ""}${formatNote(ecart)}`
}

export const LIBELLES_VERDICT: Record<VerdictCas, string> = {
  REUSSI: "Réussi",
  MAUVAISE_REPONSE: "Mauvaise réponse",
  TEMPS_DEPASSE: "Temps dépassé",
  ERREUR_COMPILATION: "Erreur de compilation",
  ERREUR_EXECUTION: "Erreur à l'exécution",
}

/** Verdict d'un cas ; une correction antérieure au verdict n'a que réussi / échoué. */
export function libelleVerdict(verdict: VerdictCas | null, reussi: boolean | null) {
  if (verdict) return LIBELLES_VERDICT[verdict]
  if (reussi === null) return "Verdict après la fermeture"
  return reussi ? "Réussi" : "Échoué"
}

/** « 12 juil. 2026 » (date du serveur lue en heure locale). */
export function formatDate(iso: string | null) {
  if (!iso) return ""
  return new Date(iso).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" })
}
