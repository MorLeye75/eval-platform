// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"
import { AuthContext, type User } from "@/context/AuthContext"
import { ProtectedRoute } from "./ProtectedRoute"

function afficher(user: User | null, chemin: string) {
  render(
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login: vi.fn(), logout: vi.fn(), updateUser: vi.fn() }}>
      <MemoryRouter initialEntries={[chemin]}>
        <Routes>
          <Route path="/login" element={<p>page de connexion</p>} />
          <Route path="/etudiant/profil" element={<ProtectedRoute allowedRoles={["ETUDIANT"]}><p>profil</p></ProtectedRoute>} />
          <Route path="/etudiant/dashboard" element={<ProtectedRoute allowedRoles={["ETUDIANT"]}><p>tableau de bord</p></ProtectedRoute>} />
          <Route path="/admin/profil" element={<ProtectedRoute allowedRoles={["ADMIN"]}><p>profil admin</p></ProtectedRoute>} />
          <Route path="/admin" element={<ProtectedRoute allowedRoles={["ADMIN"]}><p>accueil admin</p></ProtectedRoute>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

const etudiant = (provisoire: boolean): User => ({ nom: "Diop", prenom: "Awa", email: "a@ept.sn", role: "ETUDIANT", motDePasseProvisoire: provisoire })

describe("ProtectedRoute et mot de passe provisoire", () => {
  afterEach(() => cleanup())

  it("mot de passe provisoire : toute autre page renvoie vers le profil de son espace", () => {
    afficher(etudiant(true), "/etudiant/dashboard")
    expect(screen.getByText("profil")).toBeTruthy()
    cleanup()
    afficher({ ...etudiant(true), role: "ADMIN" }, "/admin")
    expect(screen.getByText("profil admin")).toBeTruthy()
  })

  it("mot de passe provisoire : le profil reste accessible", () => {
    afficher(etudiant(true), "/etudiant/profil")
    expect(screen.getByText("profil")).toBeTruthy()
  })

  it("mot de passe ordinaire : accès normal ; sans session : connexion", () => {
    afficher(etudiant(false), "/etudiant/dashboard")
    expect(screen.getByText("tableau de bord")).toBeTruthy()
    cleanup()
    afficher(null, "/etudiant/dashboard")
    expect(screen.getByText("page de connexion")).toBeTruthy()
  })
})
