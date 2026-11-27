// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { SidebarProvider } from "@/components/ui/sidebar"
import { nombreNonLues } from "@/lib/notifications"
import { AppSidebar } from "./app-sidebar"

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({ user: { prenom: "Fatou", nom: "Sarr", email: "fatou@ept.sn", role: "ETUDIANT" }, logout: vi.fn() }),
}))
vi.mock("@/lib/notifications", async (original) => ({
  ...(await original<typeof import("@/lib/notifications")>()),
  nombreNonLues: vi.fn(),
}))

// jsdom n'a pas matchMedia, utilisé par la barre latérale (mobile ou non)
beforeEach(() => {
  window.matchMedia ??= ((query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener: () => {}, removeEventListener: () => {}, addListener: () => {}, removeListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia
})

function afficher() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SidebarProvider>
          <AppSidebar />
        </SidebarProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("barre latérale étudiante : compteur des notifications (DS-15)", () => {
  afterEach(() => {
    cleanup()
    vi.mocked(nombreNonLues).mockReset()
  })

  it("non lues : leur nombre s'affiche à côté de « Notifications »", async () => {
    vi.mocked(nombreNonLues).mockResolvedValue(3)
    afficher()

    expect(await screen.findByLabelText("3 notifications non lues")).toBeTruthy()
  })

  it("aucune non lue : pas de pastille", async () => {
    vi.mocked(nombreNonLues).mockResolvedValue(0)
    afficher()

    await screen.findByText("Notifications")
    await new Promise((r) => setTimeout(r, 20))
    expect(screen.queryByLabelText(/notifications? non lues?/)).toBeNull()
  })
})
