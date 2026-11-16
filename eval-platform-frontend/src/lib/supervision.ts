import { api } from "@/lib/axios"
import type { NiveauAlerte, StatutEvaluation } from "@/types"

// Supervision en lecture seule d'une évaluation ouverte (DS-17). Le « temps réel » est un sondage : l'écran relit la
// route toutes les 15 s tant que l'évaluation est EN_COURS. Erreurs du serveur : `messageErreur` de lib/passation.

export type EtatCopie = "NON_COMMENCE" | "EN_COURS" | "SOUMIS"

export interface EtudiantSuperviseApi {
  etudiantId: number
  nom: string
  prenom: string
  etat: EtatCopie
  /** Réponses réellement données : une réponse vide enregistrée par la sauvegarde automatique ne compte pas. */
  nbReponses: number
  nbChangementsOnglet: number
  nbAlertes: number
}

export interface AlerteSupervisionApi {
  id: number
  etudiantId: number
  etudiant: string
  description: string
  niveau: NiveauAlerte
  date: string
}

export interface SupervisionApi {
  evaluationId: number
  titre: string
  statut: StatutEvaluation
  dateOuverture: string
  dateFermeture: string
  nbQuestions: number
  nbAttendus: number
  nbCommences: number
  nbSoumis: number
  nbAlertes: number
  etudiants: EtudiantSuperviseApi[]
  alertes: AlerteSupervisionApi[]
}

/** Relecture automatique : toutes les 15 s tant que l'évaluation est ouverte, jamais une fois terminée. */
export const INTERVALLE_SUPERVISION_MS = 15_000

export const cleSupervision = (id: string | number) => ["enseignant-supervision", String(id)] as const

export async function supervisionEvaluation(id: string | number) {
  const { data } = await api.get<SupervisionApi>(`/enseignant/supervision/${id}`)
  return data
}

/** Temps restant avant la fermeture : « Terminé » une fois passée. */
export function tempsRestant(dateFermeture: string, maintenant: number) {
  const diff = new Date(dateFermeture).getTime() - maintenant
  if (diff <= 0) return "Terminé"
  const min = Math.floor(diff / 60000)
  const sec = Math.floor((diff % 60000) / 1000)
  return `${min}m ${sec}s`
}

/** Progression d'un étudiant en % (0 sans question). */
export function pourcentage(nbReponses: number, nbQuestions: number) {
  return nbQuestions > 0 ? Math.round((nbReponses / nbQuestions) * 100) : 0
}
