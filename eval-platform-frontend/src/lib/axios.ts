import axios, { isAxiosError } from "axios"
import { viderSession } from "@/lib/session"

/** Au-delà, une requête figée (réseau instable) échoue au lieu de bloquer la file des sauvegardes et la soumission. */
export const DELAI_MAX_REQUETE_MS = 15_000

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api",
  timeout: DELAI_MAX_REQUETE_MS,
})

// Avant chaque requête : on attache le token JWT s'il existe
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token")
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * Après chaque réponse : si le serveur dit "401 non autorisé", le token est invalide/expiré
 * → on vide la session (le rechargement vers /login efface aussi le cache des requêtes).
 * Les réponses d'examen pas encore envoyées sont gardées pour l'étudiant qui se reconnecte.
 * Exceptions : un 401 sur /auth/login = mauvais identifiants, la page ne doit
 * pas être rechargée pour que le message d'erreur reste affiché ; un 401 sur /auth/logout (jeton déjà expiré)
 * arrive après la déconnexion, peut-être pendant la session d'un autre compte qu'il ne doit pas effacer.
 */
export function gererErreurReponse(
  error: unknown,
  rediriger: (url: string) => void = (url) => {
    window.location.href = url
  }
) {
  if (isAxiosError(error)) {
    const url = error.config?.url ?? ""
    const estConnexionOuDeconnexion = url.includes("/auth/login") || url.includes("/auth/logout")
    if (error.response?.status === 401 && !estConnexionOuDeconnexion) {
      viderSession({ garderTamponsCopies: true })
      rediriger("/login")
    }
  }
  return Promise.reject(error)
}

api.interceptors.response.use((response) => response, (error) => gererErreurReponse(error))
