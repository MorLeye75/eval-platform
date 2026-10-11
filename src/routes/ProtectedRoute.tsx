import { Navigate } from "react-router-dom"
import { useAuth, type Role } from "@/context/AuthContext"

export function ProtectedRoute({
  children,
  allowedRoles,
}: {
  children: React.ReactNode
  allowedRoles: Role[]
}) {
  const { user, isAuthenticated } = useAuth()

  // Pas connecté → direction login
  if (!isAuthenticated || !user) return <Navigate to="/login" replace />

  // Connecté mais pas le bon rôle (ex: un étudiant tente /admin) → renvoyé vers son propre espace
  if (!allowedRoles.includes(user.role)) {
    if (user.role === "ADMIN") return <Navigate to="/admin" replace />
    if (user.role === "ENSEIGNANT") return <Navigate to="/enseignant" replace />
    return <Navigate to="/etudiant/dashboard" replace />
  }

  return <>{children}</>
}