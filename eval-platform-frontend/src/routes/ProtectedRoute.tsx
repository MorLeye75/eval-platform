import { Navigate, useLocation } from "react-router-dom"
import { useAuth, type Role } from "@/context/AuthContext"
import { accueilPour } from "@/lib/roles"

export function ProtectedRoute({
  children,
  allowedRoles,
}: {
  children: React.ReactNode
  allowedRoles: Role[]
}) {
  const { user, isAuthenticated } = useAuth()
  const { pathname } = useLocation()

  // Pas connecté → direction login
  if (!isAuthenticated || !user) return <Navigate to="/login" replace />

  // Connecté mais pas le bon rôle (ex: un étudiant tente /admin) → renvoyé vers son propre espace
  if (!allowedRoles.includes(user.role)) {
    return <Navigate to={accueilPour(user.role)} replace />
  }

  // Mot de passe provisoire : le profil est la seule page offerte (le serveur refuse le reste en 403)
  if (user.motDePasseProvisoire && !pathname.endsWith("/profil")) {
    return <Navigate to={`${accueilPour(user.role).split("/").slice(0, 2).join("/")}/profil`} replace />
  }

  return <>{children}</>
}