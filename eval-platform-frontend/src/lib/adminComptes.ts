import { api } from "@/lib/axios"
import type { Role } from "@/types"

// Administration des comptes (DS-16). Les mots de passe provisoires ne sont renvoyés qu'une fois par le serveur :
// ils ne sont jamais mis en cache ni écrits dans le stockage du navigateur. Erreurs : `messageErreur` de lib/passation.

export interface CompteAdminApi {
  id: number
  nom: string
  prenom: string
  email: string
  role: Role
  actif: boolean
  dateCreation: string
  derniereConnexion: string | null
  promotionId: number | null
  promotion: string | null
  numEtudiant: string | null
  matricule: string | null
}

export interface CompteRequete {
  nom: string
  prenom: string
  email: string
  promotionId: number | null
  numEtudiant: string | null
  matricule: string | null
}

export interface CreationRequete extends CompteRequete {
  role: Role
}

export interface CompteCreeApi {
  utilisateur: CompteAdminApi
  motDePasseProvisoire: string
}

export interface ImportApi {
  importe: boolean
  nbCrees: number
  erreurs: { ligne: number; message: string }[]
  comptes: { ligne: number; email: string; motDePasseProvisoire: string }[]
}

export const CLE_COMPTES_ADMIN = ["admin-comptes"] as const

export async function listerComptes() {
  const { data } = await api.get<CompteAdminApi[]>("/admin/utilisateurs")
  return data
}

export async function creerCompte(requete: CreationRequete) {
  const { data } = await api.post<CompteCreeApi>("/admin/utilisateurs", requete)
  return data
}

export async function modifierCompte(id: number, requete: CompteRequete) {
  const { data } = await api.put<CompteAdminApi>(`/admin/utilisateurs/${id}`, requete)
  return data
}

export async function changerEtatCompte(id: number, actif: boolean) {
  const { data } = await api.post<CompteAdminApi>(`/admin/utilisateurs/${id}/${actif ? "activer" : "desactiver"}`)
  return data
}

export async function reinitialiserMotDePasse(id: number) {
  const { data } = await api.post<{ motDePasseProvisoire: string }>(`/admin/utilisateurs/${id}/reinitialiser-mot-de-passe`)
  return data
}

export async function creerPromotion(nom: string, annee: string) {
  const { data } = await api.post<{ id: number; nom: string; annee: string }>("/admin/promotions", { nom, annee })
  return data
}

export async function renommerPromotion(id: number, nom: string, annee: string) {
  const { data } = await api.put<{ id: number; nom: string; annee: string }>(`/admin/promotions/${id}`, { nom, annee })
  return data
}

/** 409 si la promotion compte des étudiants ou des évaluations : le message du serveur est affiché tel quel. */
export async function supprimerPromotion(id: number) {
  await api.delete(`/admin/promotions/${id}`)
}

/** 422 = lignes en erreur (rien n'est importé) : la réponse a la même forme, elle est rendue au lieu de lever. */
export async function importerEtudiants(fichier: File): Promise<ImportApi> {
  const formulaire = new FormData()
  formulaire.append("fichier", fichier)
  const reponse = await api.post<ImportApi>("/admin/etudiants/import", formulaire, {
    validateStatus: (s) => s === 200 || s === 422,
  })
  return reponse.data
}

/** Une cellule qui commence par = + - @ serait interprétée comme une formule par un tableur : on la neutralise. */
function cellule(valeur: string) {
  const sure = /^[=+\-@\t\r]/.test(valeur) ? `'${valeur}` : valeur
  return /[;"\n]/.test(sure) ? `"${sure.replace(/"/g, '""')}"` : sure
}

/** Fichier des mots de passe provisoires, généré dans le navigateur à partir de la réponse de l'import. */
export function csvMotsDePasse(comptes: ImportApi["comptes"]) {
  return ["email;motDePasseProvisoire", ...comptes.map((c) => `${cellule(c.email)};${cellule(c.motDePasseProvisoire)}`)].join("\n")
}
