// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { listerNotifications, marquerNotificationLue, nombreNonLues, toutMarquerLu } from "@/lib/notifications"
import type { NotificationItem, PageNotifications, Role } from "@/types"
import NotificationsPage from "./NotificationsPage"

const navigate = vi.fn()
vi.mock("react-router-dom", async (original) => ({
  ...(await original<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}))
let role: Role = "ETUDIANT"
vi.mock("@/context/AuthContext", () => ({ useAuth: () => ({ user: { role } }) }))
vi.mock("@/lib/notifications", async (original) => ({
  ...(await original<typeof import("@/lib/notifications")>()),
  listerNotifications: vi.fn(),
  nombreNonLues: vi.fn(),
  marquerNotificationLue: vi.fn(),
  toutMarquerLu: vi.fn(),
}))

function notif(n: Partial<NotificationItem>): NotificationItem {
  return {
    id: 1, type: "EVALUATION_OUVERTE", message: "L'évaluation « Réseaux » est ouverte jusqu'au 3 déc. 2026 à 18h00.",
    lue: false, dateEnvoi: "2026-11-14T08:00:00", evaluationId: 7, soumissionId: null, ...n,
  }
}

const RESULTAT = notif({ id: 2, type: "RESULTAT_PUBLIE", message: "Les résultats de « Réseaux » sont publiés.", soumissionId: 90 })
const OUVERTE = notif({ id: 1 })
const LUE = notif({ id: 3, message: "Ancienne notification", lue: true })

function page(notifications: NotificationItem[], total = notifications.length, p = 0, taille = 20): PageNotifications {
  return { notifications, page: p, taille, total }
}

function afficher() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe("notifications branchées sur l'API (DS-15)", () => {
  beforeEach(() => {
    role = "ETUDIANT"
    navigate.mockReset()
    vi.mocked(listerNotifications).mockReset().mockResolvedValue(page([RESULTAT, OUVERTE, LUE]))
    vi.mocked(nombreNonLues).mockReset().mockResolvedValue(2)
    vi.mocked(marquerNotificationLue).mockReset().mockResolvedValue(undefined)
    vi.mocked(toutMarquerLu).mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => cleanup())

  it("affiche les notifications du serveur et le nombre de non lues (aucune donnée simulée)", async () => {
    afficher()

    expect(await screen.findByText("Les résultats de « Réseaux » sont publiés.")).toBeTruthy()
    expect(screen.getByText("Ancienne notification")).toBeTruthy()
    expect(screen.getByText("2 notifications non lues.")).toBeTruthy()
    expect(screen.queryByText(/Votre note/)).toBeNull() // plus de messages simulés
    expect(listerNotifications).toHaveBeenCalledWith(0)
  })

  it("clic sur « résultats publiés » : marquée comme lue, puis ouverture des résultats de la copie", async () => {
    afficher()

    fireEvent.click(await screen.findByText("Les résultats de « Réseaux » sont publiés."))

    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/etudiant/resultats/90"))
    expect(marquerNotificationLue).toHaveBeenCalledWith(2)
  })

  it("clic sur « évaluation ouverte » : ouverture du détail de l'évaluation", async () => {
    afficher()

    fireEvent.click(await screen.findByText(/est ouverte jusqu'au/))

    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/etudiant/evaluations/7"))
  })

  it("clic sur une notification déjà lue : pas de nouvel appel au serveur", async () => {
    afficher()

    fireEvent.click(await screen.findByText("Ancienne notification"))

    await waitFor(() => expect(navigate).toHaveBeenCalled())
    expect(marquerNotificationLue).not.toHaveBeenCalled()
  })

  it("enseignant : la notification est marquée comme lue, sans navigation (écrans d'Aminou)", async () => {
    role = "ENSEIGNANT"
    afficher()

    fireEvent.click(await screen.findByText(/est ouverte jusqu'au/))

    await waitFor(() => expect(marquerNotificationLue).toHaveBeenCalledWith(1))
    expect(navigate).not.toHaveBeenCalled()
  })

  it("« Tout marquer comme lu » : appel au serveur, puis liste et compteur relus", async () => {
    afficher()
    await screen.findByText("Ancienne notification")
    vi.mocked(listerNotifications).mockResolvedValue(page([{ ...RESULTAT, lue: true }, { ...OUVERTE, lue: true }, LUE]))
    vi.mocked(nombreNonLues).mockResolvedValue(0)

    fireEvent.click(screen.getByRole("button", { name: /Tout marquer comme lu/ }))

    expect(await screen.findByText("Tout est lu.")).toBeTruthy()
    expect(toutMarquerLu).toHaveBeenCalledTimes(1)
  })

  it("plus de notifications que la page : « Afficher plus » charge la page suivante", async () => {
    vi.mocked(listerNotifications)
      .mockResolvedValueOnce(page([RESULTAT, OUVERTE], 3, 0, 2))
      .mockResolvedValueOnce(page([LUE], 3, 1, 2))
    afficher()
    await screen.findByText("Les résultats de « Réseaux » sont publiés.")
    expect(screen.queryByText("Ancienne notification")).toBeNull()

    fireEvent.click(screen.getByRole("button", { name: /Afficher plus/ }))

    expect(await screen.findByText("Ancienne notification")).toBeTruthy()
    expect(listerNotifications).toHaveBeenLastCalledWith(1)
    expect(screen.queryByRole("button", { name: /Afficher plus/ })).toBeNull()
  })

  it("aucune notification : message vide", async () => {
    vi.mocked(listerNotifications).mockResolvedValue(page([]))
    vi.mocked(nombreNonLues).mockResolvedValue(0)
    afficher()

    expect(await screen.findByText("Aucune notification.")).toBeTruthy()
  })

  it("serveur injoignable : message d'erreur, pas une liste vide trompeuse", async () => {
    vi.mocked(listerNotifications).mockRejectedValue(new Error("Network Error"))
    afficher()

    expect(await screen.findByText(/Impossible de charger vos notifications/)).toBeTruthy()
    expect(screen.queryByText("Aucune notification.")).toBeNull()
  })
})
