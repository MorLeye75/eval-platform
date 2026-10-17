import { createContext, useContext, useState, type ReactNode } from "react"
import { api } from "@/lib/axios"
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

export function AuthProvider({ children }: { children: ReactNode }) {
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

  function logout() {
    localStorage.removeItem("token")
    localStorage.removeItem("user")
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