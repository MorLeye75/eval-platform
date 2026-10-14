import type { Role } from "@/types"

/** Page d'accueil de chaque rôle (utilisée par login, ProtectedRoute, IndexRedirect). */
export function accueilPour(role: Role): string {
  if (role === "ADMIN") return "/admin"
  if (role === "ENSEIGNANT") return "/enseignant"
  return "/etudiant/dashboard"
}
