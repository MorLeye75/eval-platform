// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { configurationSysteme } from "@/lib/adminVue"
import ParametresSysteme from "./ParametresSysteme"

vi.mock("@/lib/adminVue", async (original) => ({
  ...(await original<typeof import("@/lib/adminVue")>()),
  configurationSysteme: vi.fn(),
}))

const CONFIG = {
  sections: [
    { titre: "Passation", parametres: [{ libelle: "Marge de tolérance après la fermeture", valeur: "2 min", source: "CODE" }] },
    { titre: "Sécurité des comptes", parametres: [{ libelle: "Échecs de connexion avant blocage", valeur: "5", source: "DEPLOIEMENT" }] },
  ],
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><ParametresSysteme /></QueryClientProvider>)
}

describe("paramètres système : configuration en vigueur (DS-18)", () => {
  beforeEach(() => { vi.mocked(configurationSysteme).mockResolvedValue(CONFIG) })
  afterEach(() => { cleanup(); vi.clearAllMocks() })

  it("affiche les valeurs du serveur par section, avec leur source", async () => {
    afficher()
    const passation = (await screen.findByText("Passation")).closest("div[class*='rounded-2xl']") as HTMLElement
    expect(within(passation).getByText("Marge de tolérance après la fermeture")).toBeTruthy()
    expect(within(passation).getByText("2 min")).toBeTruthy()
    expect(within(passation).getByText("Fixé dans le code")).toBeTruthy()
    expect(screen.getByText("Configuration du déploiement")).toBeTruthy()
    expect(screen.getByText("Échecs de connexion avant blocage")).toBeTruthy()
  })

  it("lecture seule : aucun interrupteur, aucun champ, aucun bouton d'enregistrement ; plus de valeurs inventées", async () => {
    afficher()
    await screen.findByText("Passation")
    expect(screen.queryByRole("switch")).toBeNull()
    expect(screen.queryByRole("textbox")).toBeNull()
    expect(screen.queryByRole("button", { name: /Enregistrer/ })).toBeNull()
    expect(screen.queryByText(/Aperçu/)).toBeNull()
    expect(screen.queryByText(/Sauvegarde automatique/)).toBeNull() // réglage de l'ancien écran factice
    expect(screen.getByText(/Cette page ne modifie rien/)).toBeTruthy()
  })

  it("chargement impossible : message et nouvel essai", async () => {
    vi.mocked(configurationSysteme).mockRejectedValueOnce(new Error("réseau")).mockResolvedValue(CONFIG)
    afficher()
    expect((await screen.findByRole("alert")).textContent).toContain("Impossible de charger la configuration")
    fireEvent.click(screen.getByRole("button", { name: "Réessayer" }))
    await waitFor(() => expect(screen.getByText("Passation")).toBeTruthy())
  })
})
