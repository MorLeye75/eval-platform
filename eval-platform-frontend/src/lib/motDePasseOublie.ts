import { api } from "@/lib/axios"

/**
 * Demande d'un lien de réinitialisation. Le serveur répond 202 que le compte existe ou non : l'écran affiche
 * toujours la même confirmation.
 */
export async function demanderReinitialisation(email: string) {
  await api.post("/auth/mot-de-passe-oublie", { email })
}

/** Nouveau mot de passe depuis le lien reçu. Lien inutilisable : 400 avec le code JETON_INVALIDE. */
export async function reinitialiserMotDePasse(token: string, motDePasse: string) {
  await api.post("/auth/reinitialiser-mot-de-passe", { token, motDePasse })
}
