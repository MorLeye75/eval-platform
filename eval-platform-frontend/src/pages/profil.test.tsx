// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { changerMotDePasse, lireProfil } from "@/lib/profil"
import type { Profil } from "@/types"
import ProfilePage from "./ProfilePage"

vi.mock("@/lib/profil", () => ({ lireProfil: vi.fn(), changerMotDePasse: vi.fn() }))

const ETUDIANT: Profil = {
  id: 3, nom: "Diop", prenom: "Awa", email: "awa.diop@ept.sn", role: "ETUDIANT",
  numEtudiant: "ETU-2026-042", promotion: "DIC2 Informatique",
}

const config = { url: "/profil/mot-de-passe", headers: new AxiosHeaders() } as InternalAxiosRequestConfig
const erreur = (status: number, detail: string) =>
  new AxiosError("erreur", "ERR_BAD_REQUEST", config, null, { status, statusText: "", headers: {}, config, data: { detail } })

function afficher() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ProfilePage />
    </QueryClientProvider>
  )
}

function saisir(libelle: string, valeur: string) {
  fireEvent.change(screen.getByLabelText(libelle), { target: { value: valeur } })
}

describe("profil branché sur l'API", () => {
  beforeEach(() => {
    vi.mocked(lireProfil).mockReset().mockResolvedValue(ETUDIANT)
    vi.mocked(changerMotDePasse).mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => cleanup())

  it("identité du serveur en lecture seule, avec matricule et promotion ; aucun champ d'identité modifiable", async () => {
    afficher()

    expect(await screen.findByText("awa.diop@ept.sn")).toBeTruthy()
    expect(screen.getByText("ETU-2026-042")).toBeTruthy()
    expect(screen.getByText("DIC2 Informatique")).toBeTruthy()
    expect(screen.getByText(/administrat/)).toBeTruthy() // qui contacter pour corriger son identité
    expect(screen.queryByRole("button", { name: /^Enregistrer$/ })).toBeNull()
    // Seuls les trois champs de mot de passe sont saisissables
    expect(document.querySelectorAll("input:not([type=password])")).toHaveLength(0)
  })

  it("changement réussi : le serveur est appelé, le formulaire est vidé, confirmation affichée", async () => {
    afficher()
    await screen.findByText("awa.diop@ept.sn")

    saisir("Mot de passe actuel", "ancienMotDePasse")
    saisir("Nouveau mot de passe", "nouveauMotDePasse")
    saisir("Confirmation", "nouveauMotDePasse")
    fireEvent.click(screen.getByRole("button", { name: /Modifier le mot de passe/ }))

    expect(await screen.findByText("Mot de passe modifié")).toBeTruthy()
    expect(changerMotDePasse).toHaveBeenCalledWith("ancienMotDePasse", "nouveauMotDePasse")
    expect((screen.getByLabelText("Mot de passe actuel") as HTMLInputElement).value).toBe("")
  })

  it("refus du serveur (mot de passe actuel faux, 429) : son message est affiché, pas de confirmation", async () => {
    vi.mocked(changerMotDePasse).mockRejectedValueOnce(erreur(400, "Le mot de passe actuel est incorrect."))
    afficher()
    await screen.findByText("awa.diop@ept.sn")

    saisir("Mot de passe actuel", "faux")
    saisir("Nouveau mot de passe", "nouveauMotDePasse")
    saisir("Confirmation", "nouveauMotDePasse")
    fireEvent.click(screen.getByRole("button", { name: /Modifier le mot de passe/ }))

    expect(await screen.findByText("Le mot de passe actuel est incorrect.")).toBeTruthy()
    expect(screen.queryByText("Mot de passe modifié")).toBeNull()
  })

  it("confirmation différente ou mot de passe trop court : refusé sans appeler le serveur", async () => {
    afficher()
    await screen.findByText("awa.diop@ept.sn")

    saisir("Mot de passe actuel", "ancienMotDePasse")
    saisir("Nouveau mot de passe", "nouveauMotDePasse")
    saisir("Confirmation", "autreChose")
    fireEvent.click(screen.getByRole("button", { name: /Modifier le mot de passe/ }))
    expect(await screen.findByText(/ne correspondent pas/)).toBeTruthy()

    saisir("Nouveau mot de passe", "court")
    saisir("Confirmation", "court")
    fireEvent.click(screen.getByRole("button", { name: /Modifier le mot de passe/ }))
    expect(await screen.findByText(/au moins 8 caractères/)).toBeTruthy()

    expect(changerMotDePasse).not.toHaveBeenCalled()
  })

  it("profil illisible (serveur injoignable) : message d'erreur, pas de page vide", async () => {
    vi.mocked(lireProfil).mockRejectedValueOnce(new Error("Network Error"))
    afficher()

    await waitFor(() => expect(screen.getByText(/Impossible de charger votre profil/)).toBeTruthy())
  })
})
