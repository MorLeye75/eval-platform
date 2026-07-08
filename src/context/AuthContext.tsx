import { createContext, useContext, useState, type ReactNode } from "react"
import { api } from "@/lib/axios"

export type Role = "ETUDIANT" | "ENSEIGNANT" | "ADMIN"

interface User {
  nom: string
  prenom: string
  email: string
  role: Role
}

interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  login: (email: string, motDePasse: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  // Au démarrage, on regarde si un utilisateur était déjà connecté
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem("user")
    return stored ? JSON.parse(stored) : null
  })

  async function login(email: string, motDePasse: string) {
    const response = await api.post("/auth/login", { email, motDePasse })
    const { token, ...userData } = response.data

    localStorage.setItem("token", token)
    localStorage.setItem("user", JSON.stringify(userData))
    setUser(userData)
  }

  function logout() {
    localStorage.removeItem("token")
    localStorage.removeItem("user")
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error("useAuth doit être utilisé dans un AuthProvider")
  return context
}