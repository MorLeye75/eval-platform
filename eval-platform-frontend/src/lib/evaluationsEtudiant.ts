import { api } from "@/lib/axios"
import type { EvaluationEtudiant } from "@/types"

/** Évaluations visibles par l'étudiant connecté (sa promotion), avec l'état de sa copie. */
export async function mesEvaluations() {
  const { data } = await api.get<EvaluationEtudiant[]>("/etudiant/evaluations")
  return data
}

export const CLE_MES_EVALUATIONS = ["mes-evaluations"] as const

export type EtatEvaluation = "disponible" | "a_venir" | "terminee"

/**
 * État affiché à l'étudiant. Copie déjà remise, évaluation terminée ou fermée : terminée ; avant l'ouverture :
 * à venir ; sinon disponible (y compris une copie en cours à reprendre). Le serveur reste juge au démarrage.
 */
export function etatEvaluation(e: EvaluationEtudiant, maintenant = new Date()): EtatEvaluation {
  if (e.statutSoumission && e.statutSoumission !== "EN_COURS") return "terminee"
  if (e.statut === "TERMINEE") return "terminee"
  if (maintenant < new Date(e.dateOuverture)) return "a_venir"
  if (maintenant > new Date(e.dateFermeture)) return "terminee"
  return "disponible"
}

export type TypeEpreuve = "QCM" | "CODE" | "OUVERTE" | "QUIZ" | "MIXTE"

/** Type d'épreuve déduit des questions : un seul type, ou mixte (plusieurs types mélangés). */
export function typeEpreuve(e: EvaluationEtudiant): TypeEpreuve {
  const types = new Set(e.typesQuestions)
  if (types.size !== 1) return "MIXTE"
  const [type] = types
  return type === "QUIZ_CHRONOMETREE" ? "QUIZ" : type
}

const CHEMINS: Record<TypeEpreuve, string | null> = {
  QCM: "qcm",
  CODE: "code",
  OUVERTE: "ouverte",
  QUIZ: "quiz",
  MIXTE: null, // écran unique pour les épreuves mixtes : en préparation
}

/** Chemin de l'écran de passation, ou null si aucun écran ne sait encore l'afficher. */
export function cheminPassation(e: EvaluationEtudiant): string | null {
  const chemin = CHEMINS[typeEpreuve(e)]
  return chemin ? `/examen/${e.id}/${chemin}` : null
}

/** « 12 juil. 2026 à 23:59 » (date du serveur lue en heure locale). */
export function formatDateHeure(iso: string) {
  const d = new Date(iso)
  const date = d.toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" })
  const heure = d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })
  return `${date} à ${heure}`
}
