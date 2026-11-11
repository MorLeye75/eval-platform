import { api } from "@/lib/axios"
import type { Profil } from "@/types"

/** Profil de l'utilisateur connecté (identité en lecture seule, gérée par l'administrateur). */
export async function lireProfil() {
  const { data } = await api.get<Profil>("/profil")
  return data
}

/**
 * Changement de son mot de passe. Mot de passe actuel faux : 400 (pas 401, qui déconnecterait) ;
 * trop d'échecs récents : 429, comme à la connexion.
 */
export async function changerMotDePasse(motDePasseActuel: string, nouveauMotDePasse: string) {
  await api.put("/profil/mot-de-passe", { motDePasseActuel, nouveauMotDePasse })
}
