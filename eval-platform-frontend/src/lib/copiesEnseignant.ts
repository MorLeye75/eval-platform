import { api } from "@/lib/axios"

// Copie d'un étudiant vue par son enseignant, et révision d'une note avant la publication des résultats. Le serveur
// reste juge : note bornée au barème, motif obligatoire, refus (409) dès que les résultats sont publiés.

export type VerdictCas = "REUSSI" | "MAUVAISE_REPONSE" | "TEMPS_DEPASSE" | "ERREUR_COMPILATION" | "ERREUR_EXECUTION"

export interface CasCopieApi {
  entree: string | null
  sortieAttendue: string | null
  sortieObtenue: string | null
  verdict: VerdictCas | null
  reussi: boolean
  masque: boolean
  messageErreur: string | null
}

export interface RevisionApi {
  ancienneNote: number
  nouvelleNote: number
  motif: string
  auteur: string
  reviseeLe: string
}

export interface ReponseCopieApi {
  /** null si l'étudiant n'a rien enregistré pour cette question. */
  reponseId: number | null
  ordre: number | null
  type: "QCM" | "CODE" | "OUVERTE" | "QUIZ_CHRONOMETREE" | null
  enonce: string
  pointsMax: number
  /** null (jamais 0) tant que la réponse n'est pas notée. */
  note: number | null
  notee: boolean
  commentaire: string | null
  texte: string | null
  code: string | null
  langage: string | null
  choix: { texte: string; coche: boolean; correct: boolean }[]
  casDeTests: CasCopieApi[]
  revisions: RevisionApi[]
}

export interface CopieEnseignantApi {
  soumissionId: number
  evaluationId: number
  evaluation: string
  etudiant: string
  numEtudiant: string | null
  statut: "EN_COURS" | "SOUMIS" | "EN_CORRECTION" | "EVALUE"
  noteFinale: number | null
  bareme: number
  resultatsPublies: boolean
  /** Vrai tant que les résultats ne sont pas publiés et que la copie est corrigée (ou en correction). */
  notesModifiables: boolean
  dateRemise: string | null
  reponses: ReponseCopieApi[]
}

export interface RevisionNoteApi {
  reponseId: number
  ancienneNote: number
  nouvelleNote: number
  noteFinale: number | null
}

export const cleCopie = (soumissionId: number) => ["enseignant-copie", soumissionId] as const

export async function lireCopie(soumissionId: number) {
  const { data } = await api.get<CopieEnseignantApi>(`/enseignant/soumissions/${soumissionId}/copie`)
  return data
}

/** Révise une note déjà posée : motif obligatoire, tracé et montré à l'étudiant avec sa note. */
export async function reviserNote(reponseId: number, note: number, motif: string) {
  const { data } = await api.put<RevisionNoteApi>(`/enseignant/reponses/${reponseId}/revision`, { note, motif })
  return data
}

export const LIBELLE_VERDICT: Record<VerdictCas, string> = {
  REUSSI: "Réussi",
  MAUVAISE_REPONSE: "Mauvaise réponse",
  TEMPS_DEPASSE: "Temps dépassé",
  ERREUR_COMPILATION: "Erreur de compilation",
  ERREUR_EXECUTION: "Erreur à l'exécution",
}
