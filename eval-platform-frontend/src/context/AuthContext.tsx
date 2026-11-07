import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/axios"
import { viderSession } from "@/lib/session"
import type { Role, Utilisateur } from "@/types"

export type { Role }

export interface User extends Partial<Utilisateur> {
  nom: string
  prenom: string
  email: string
  role: Role
}

interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  login: (email: string, motDePasse: string) => Promise<User>
  logout: () => void
  /** Met à jour l'utilisateur connecté (contexte + localStorage), ex. après édition du profil. */
  updateUser: (patch: Partial<Pick<User, "nom" | "prenom" | "email">>) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

/** Réponse de /auth/login ou /auth/me sans le jeton (stocké à part). */
function sansJeton(donnees: User & { token?: string | null }): User {
  const utilisateur = { ...donnees }
  delete utilisateur.token
  return utilisateur
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  // Au démarrage, on regarde si un utilisateur était déjà connecté
  const [user, setUser] = useState<User | null>(() => {
    try {
      const stored = localStorage.getItem("user")
      return stored ? JSON.parse(stored) : null
    } catch {
      localStorage.removeItem("user")
      localStorage.removeItem("token")
      return null
    }
  })

  async function login(email: string, motDePasse: string): Promise<User> {
    const response = await api.post("/auth/login", { email, motDePasse })
    const { token, ...userData } = response.data

    localStorage.setItem("token", token)
    localStorage.setItem("user", JSON.stringify(userData))
    setUser(userData)
    return userData
  }

  // L'utilisateur du localStorage (rôle compris) n'est qu'un cache : il est revalidé par le serveur au démarrage.
  // Jeton refusé (401) : l'intercepteur vide la session et renvoie au login. Réseau absent : le cache est gardé.
  useEffect(() => {
    if (!localStorage.getItem("token")) return
    let annule = false
    api
      .get("/auth/me")
      .then(({ data }) => {
        // Déconnexion pendant la requête : ne pas restaurer l'utilisateur
        if (annule || !localStorage.getItem("token")) return
        const utilisateur = sansJeton(data)
        localStorage.setItem("user", JSON.stringify(utilisateur))
        setUser(utilisateur)
      })
      .catch(() => {})
    return () => {
      annule = true
    }
  }, [])

  /**
   * Déconnexion volontaire (poste partagé) : jeton, utilisateur, brouillon d'évaluation, réponses locales
   * des copies et cache des requêtes (copies, évaluations…) sont effacés.
   */
  function logout() {
    viderSession()
    queryClient.clear()
    setUser(null)
  }

  function updateUser(patch: Partial<Pick<User, "nom" | "prenom" | "email">>) {
    setUser((current) => {
      if (!current) return current
      const next = { ...current, ...patch }
      localStorage.setItem("user", JSON.stringify(next))
      return next
    })
  }

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error("useAuth doit être utilisé dans un AuthProvider")
  return context
}