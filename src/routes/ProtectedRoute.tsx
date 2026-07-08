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
  if (!isAuthenticated) return <Navigate to="/login" replace />

  // Connecté mais pas le bon rôle (ex: un étudiant tente /admin) → renvoyé
  if (!allowedRoles.includes(user!.role)) return <Navigate to="/" replace />

  return <>{children}</>
}