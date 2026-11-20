import { api } from "@/lib/axios"
import type { NiveauAlerte } from "@/types"

// Vue d'ensemble de l'administrateur (DS-18) : trois lectures seules du serveur. Un indicateur `null` signifie « pas de
// donnée » (aucune copie notée) : l'écran affiche « — », jamais un 0 trompeur.

export interface ServiceApi { nom: string; etat: string; detail: string | null }

export interface TableauDeBordApi {
  nbUtilisateurs: number
  parRole: { etudiants: number; enseignants: number; admins: number }
  nbEvaluationsEnCours: number
  nbEvaluationsPubliees: number
  nbSoumissions7j: number
  nbAlertes7j: number
  services: ServiceApi[]
}

export interface EvaluationEnCoursApi {
  id: number
  titre: string
  enseignant: string
  nbAttendus: number
  nbCommences: number
  nbSoumis: number
  dateOuverture: string
  dateFermeture: string
}

export interface AlerteGlobaleApi {
  id: number
  evaluationId: number
  evaluation: string
  etudiant: string
  description: string
  niveau: NiveauAlerte
  date: string
}

export interface SupervisionGlobaleApi { evaluations: EvaluationEnCoursApi[]; alertes: AlerteGlobaleApi[] }

export interface StatPromotionApi {
  id: number
  nom: string
  annee: string
  effectif: number
  nbEvaluations: number
  nbCopiesNotees: number
  moyenneSur20: number | null
  tauxReussite: number | null
}

export interface StatistiquesGlobalesApi {
  nbEvaluations: number
  nbCopies: number
  nbCopiesNotees: number
  tauxRemise: number | null
  moyenneSur20: number | null
  tauxReussite: number | null
  promotions: StatPromotionApi[]
}

export const CLE_ADMIN_TABLEAU = ["admin-tableau-de-bord"] as const
export const CLE_ADMIN_SUPERVISION = ["admin-supervision"] as const
export const CLE_ADMIN_STATISTIQUES = ["admin-statistiques"] as const
/** La supervision globale est relue toutes les 30 secondes. */
export const INTERVALLE_SUPERVISION_GLOBALE_MS = 30_000

export async function tableauDeBordAdmin() {
  return (await api.get<TableauDeBordApi>("/admin/tableau-de-bord")).data
}
export async function supervisionGlobale() {
  return (await api.get<SupervisionGlobaleApi>("/admin/supervision")).data
}
export async function statistiquesGlobales() {
  return (await api.get<StatistiquesGlobalesApi>("/admin/statistiques")).data
}

/** « 13,4 / 20 » ou « — » quand l'indicateur n'existe pas. */
export function sur20(valeur: number | null) {
  return valeur === null ? "—" : `${valeur.toLocaleString("fr-FR", { maximumFractionDigits: 2 })} / 20`
}

/** « 78 % » ou « — » quand l'indicateur n'existe pas. */
export function pourcent(valeur: number | null) {
  return valeur === null ? "—" : `${valeur.toLocaleString("fr-FR", { maximumFractionDigits: 1 })} %`
}

// ---------------------------------------------------------------- configuration en vigueur (DS-18, section D)

export interface ParametreApi { libelle: string; valeur: string; source: "CODE" | "DEPLOIEMENT" | string }
export interface SectionConfigurationApi { titre: string; parametres: ParametreApi[] }

export const CLE_CONFIGURATION = ["admin-configuration"] as const

/** Ce que le serveur applique réellement, en lecture seule ; jamais de secret. */
export async function configurationSysteme() {
  const { data } = await api.get<{ sections: SectionConfigurationApi[] }>("/admin/configuration")
  return data
}
