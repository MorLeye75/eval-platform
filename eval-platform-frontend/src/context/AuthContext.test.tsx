// @vitest-environment jsdom
import type { ReactNode } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, cleanup, renderHook, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { AuthProvider, useAuth } from "@/context/AuthContext"
import { api } from "@/lib/axios"
import { CLE_BROUILLON_EVALUATION, cleTamponCopie } from "@/lib/session"

const UTILISATEUR_STOCKE = { id: 1, nom: "Niang", prenom: "Aminou", email: "etudiant@ept.sn", role: "ETUDIANT" }

function rendre(queryClient: QueryClient) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
  return renderHook(() => useAuth(), { wrapper })
}

describe("AuthContext", () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    localStorage.setItem("token", "jeton")
    localStorage.setItem("user", JSON.stringify(UTILISATEUR_STOCKE))
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it("déconnexion : vide le cache des requêtes, le brouillon et les sauvegardes locales des copies", async () => {
    vi.spyOn(api, "get").mockResolvedValue({ data: { ...UTILISATEUR_STOCKE, token: null } })
    localStorage.setItem(cleTamponCopie(12), "{}")
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, "{}")
    const queryClient = new QueryClient()
    queryClient.setQueryData(["copie", 12], { questions: ["énoncé"] })
    const { result } = rendre(queryClient)

    act(() => result.current.logout())

    expect(result.current.user).toBeNull()
    expect(queryClient.getQueryData(["copie", 12])).toBeUndefined()
    expect(localStorage.getItem("token")).toBeNull()
    expect(localStorage.getItem(cleTamponCopie(12))).toBeNull()
    expect(sessionStorage.getItem(CLE_BROUILLON_EVALUATION)).toBeNull()
  })

  it("au démarrage, l'utilisateur stocké est revalidé par GET /auth/me (le rôle du localStorage ne fait pas foi)", async () => {
    const get = vi.spyOn(api, "get").mockResolvedValue({
      data: { ...UTILISATEUR_STOCKE, role: "ADMIN", token: null },
    })
    const { result } = rendre(new QueryClient())

    await waitFor(() => expect(result.current.user?.role).toBe("ADMIN"))
    expect(get).toHaveBeenCalledWith("/auth/me")
    const stocke = JSON.parse(localStorage.getItem("user")!)
    expect(stocke.role).toBe("ADMIN")
    expect(stocke).not.toHaveProperty("token")
  })

  it("sans utilisateur connecté, /auth/me n'est pas appelé", () => {
    localStorage.clear()
    const get = vi.spyOn(api, "get")

    rendre(new QueryClient())

    expect(get).not.toHaveBeenCalled()
  })
})
