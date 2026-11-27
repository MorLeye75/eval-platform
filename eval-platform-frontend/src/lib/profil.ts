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
 * Le serveur révoque les jetons émis avant le changement (autres appareils déconnectés) : le jeton renvoyé remplace
 * celui de cette session, sinon la requête suivante recevrait un 401.
 */
export async function changerMotDePasse(motDePasseActuel: string, nouveauMotDePasse: string) {
  const { data } = await api.put<{ token: string }>("/profil/mot-de-passe", { motDePasseActuel, nouveauMotDePasse })
  localStorage.setItem("token", data.token)
}
