import axios from "axios"

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api",
})

// Avant chaque requête : on attache le token JWT s'il existe
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token")
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Après chaque réponse : si le serveur dit "401 non autorisé",
// le token est invalide/expiré → on déconnecte proprement.
// Exception : un 401 sur /auth/login = mauvais identifiants, la page ne doit
// pas être rechargée pour que le message d'erreur reste affiché.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const estLogin = error.config?.url?.includes("/auth/login")
    if (error.response?.status === 401 && !estLogin) {
      localStorage.removeItem("token")
      localStorage.removeItem("user")
      window.location.href = "/login"
    }
    return Promise.reject(error)
  }
)
