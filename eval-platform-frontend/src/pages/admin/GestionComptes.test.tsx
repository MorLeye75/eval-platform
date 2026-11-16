// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  changerEtatCompte,
  creerCompte,
  importerEtudiants,
  listerComptes,
  reinitialiserMotDePasse,
  type CompteAdminApi,
} from "@/lib/adminComptes"
import { listerPromotions } from "@/lib/evaluationsEnseignant"
import GestionComptes from "./GestionComptes"

vi.mock("@/lib/adminComptes", async (original) => ({
  ...(await original<typeof import("@/lib/adminComptes")>()),
  listerComptes: vi.fn(),
  creerCompte: vi.fn(),
  modifierCompte: vi.fn(),
  changerEtatCompte: vi.fn(),
  reinitialiserMotDePasse: vi.fn(),
  importerEtudiants: vi.fn(),
  creerPromotion: vi.fn(),
}))
vi.mock("@/lib/evaluationsEnseignant", async (original) => ({
  ...(await original<typeof import("@/lib/evaluationsEnseignant")>()),
  listerPromotions: vi.fn(),
}))

const compte = (id: number, surcharge: Partial<CompteAdminApi> = {}): CompteAdminApi => ({
  id, nom: `Nom${id}`, prenom: `Prenom${id}`, email: `u${id}@ept.sn`, role: "ETUDIANT", actif: true,
  dateCreation: "2026-01-01T08:00:00", derniereConnexion: null, promotionId: 1, promotion: "DIC2", numEtudiant: `N${id}`, matricule: null,
  ...surcharge,
})

function erreurHttp(status: number, detail: string) {
  const response = { status, data: { detail }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><GestionComptes /></QueryClientProvider>)
}

describe("gestion des comptes (DS-16)", () => {
  beforeEach(() => {
    vi.mocked(listerComptes).mockResolvedValue([
      compte(1), compte(2, { role: "ENSEIGNANT", promotionId: null, promotion: null, numEtudiant: null, matricule: "M-1" }),
      compte(3, { role: "ADMIN", promotionId: null, promotion: null, numEtudiant: null }), compte(4, { actif: false }),
    ])
    vi.mocked(listerPromotions).mockResolvedValue([{ id: 1, nom: "DIC2", annee: "2026", nbEtudiants: 10 }])
    vi.spyOn(window, "confirm").mockReturnValue(true)
  })
  afterEach(() => { cleanup(); vi.clearAllMocks(); vi.restoreAllMocks() })

  it("affiche les comptes de l'API, leur statut, et filtre par rôle et par recherche", async () => {
    afficher()
    expect(await screen.findByText("Prenom1 Nom1")).toBeTruthy()
    expect(screen.getByText("4 sur 4 comptes")).toBeTruthy()
    expect(screen.getByText("Désactivé")).toBeTruthy()

    fireEvent.click(screen.getByRole("tab", { name: "Enseignants" }))
    expect(screen.getByText("1 sur 4 comptes")).toBeTruthy()

    fireEvent.click(screen.getByRole("tab", { name: "Tous" }))
    fireEvent.change(screen.getByLabelText("Rechercher un compte"), { target: { value: "u3@" } })
    expect(screen.getByText("1 sur 4 comptes")).toBeTruthy()
  })

  it("créer un étudiant : champs envoyés à l'API, mot de passe provisoire affiché une fois", async () => {
    vi.mocked(creerCompte).mockResolvedValue({ utilisateur: compte(9), motDePasseProvisoire: "Zx7Kp2Qm9Wr4" })
    afficher()
    await screen.findByText("Prenom1 Nom1")

    fireEvent.click(screen.getByRole("button", { name: /Nouveau compte/ }))
    fireEvent.change(screen.getByLabelText("Nom"), { target: { value: "Diop" } })
    fireEvent.change(screen.getByLabelText("Prénom"), { target: { value: "Moussa" } })
    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "moussa@ept.sn" } })
    await screen.findByRole("option", { name: "DIC2 (2026)" })
    fireEvent.change(screen.getByLabelText("Promotion"), { target: { value: "1" } })
    fireEvent.change(screen.getByLabelText("Numéro d'étudiant"), { target: { value: "N-42" } })
    fireEvent.click(screen.getByRole("button", { name: "Créer le compte" }))

    await waitFor(() => expect(creerCompte).toHaveBeenCalledWith({
      role: "ETUDIANT", nom: "Diop", prenom: "Moussa", email: "moussa@ept.sn", promotionId: 1, numEtudiant: "N-42", matricule: null,
    }))
    expect(await screen.findByText("Zx7Kp2Qm9Wr4")).toBeTruthy()
    // Jamais conservé dans le navigateur
    expect(JSON.stringify({ ...localStorage })).not.toContain("Zx7Kp2Qm9Wr4")
    fireEvent.click(screen.getByRole("button", { name: "Masquer" }))
    expect(screen.queryByText("Zx7Kp2Qm9Wr4")).toBeNull()
  })

  it("erreur du serveur (doublon d'e-mail, 409) : le message du serveur est affiché", async () => {
    vi.mocked(creerCompte).mockRejectedValue(erreurHttp(409, "Cet e-mail est déjà utilisé par un autre compte."))
    afficher()
    await screen.findByText("Prenom1 Nom1")
    fireEvent.click(screen.getByRole("button", { name: /Nouveau compte/ }))
    fireEvent.change(screen.getByLabelText("Rôle"), { target: { value: "ADMIN" } })
    fireEvent.change(screen.getByLabelText("Nom"), { target: { value: "A" } })
    fireEvent.change(screen.getByLabelText("Prénom"), { target: { value: "B" } })
    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "a@ept.sn" } })
    fireEvent.click(screen.getByRole("button", { name: "Créer le compte" }))
    expect(await screen.findByText("Cet e-mail est déjà utilisé par un autre compte.")).toBeTruthy()
  })

  it("désactiver : confirmation puis appel ; refus de confirmer : aucun appel ; réactiver : sans confirmation", async () => {
    vi.mocked(changerEtatCompte).mockResolvedValue(compte(1, { actif: false }))
    afficher()
    await screen.findByText("Prenom1 Nom1")

    vi.mocked(window.confirm).mockReturnValueOnce(false)
    fireEvent.click(screen.getByRole("button", { name: "Désactiver : Prenom1 Nom1" }))
    expect(changerEtatCompte).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole("button", { name: "Désactiver : Prenom1 Nom1" }))
    await waitFor(() => expect(changerEtatCompte).toHaveBeenCalledWith(1, false))

    vi.mocked(window.confirm).mockClear()
    fireEvent.click(screen.getByRole("button", { name: "Réactiver : Prenom4 Nom4" }))
    await waitFor(() => expect(changerEtatCompte).toHaveBeenCalledWith(4, true))
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it("dernier administrateur / son propre compte (409) : message du serveur affiché", async () => {
    vi.mocked(changerEtatCompte).mockRejectedValue(erreurHttp(409, "Vous ne pouvez pas désactiver votre propre compte."))
    afficher()
    await screen.findByText("Prenom3 Nom3")
    fireEvent.click(screen.getByRole("button", { name: "Désactiver : Prenom3 Nom3" }))
    expect(await screen.findByText("Vous ne pouvez pas désactiver votre propre compte.")).toBeTruthy()
  })

  it("réinitialiser le mot de passe : confirmation, puis le nouveau mot de passe est affiché", async () => {
    vi.mocked(reinitialiserMotDePasse).mockResolvedValue({ motDePasseProvisoire: "Nv5Tt8Hh3Jj6" })
    afficher()
    await screen.findByText("Prenom2 Nom2")
    fireEvent.click(screen.getByRole("button", { name: "Réinitialiser le mot de passe : Prenom2 Nom2" }))
    await waitFor(() => expect(reinitialiserMotDePasse).toHaveBeenCalledWith(2))
    expect(await screen.findByText("Nv5Tt8Hh3Jj6")).toBeTruthy()
  })

  it("import avec erreurs : rien n'est importé, chaque ligne à corriger est listée", async () => {
    vi.mocked(importerEtudiants).mockResolvedValue({
      importe: false, nbCrees: 0, comptes: [],
      erreurs: [{ ligne: 3, message: "E-mail invalide : x" }, { ligne: 5, message: "Promotion inconnue : Z" }],
    })
    afficher()
    await screen.findByText("Prenom1 Nom1")
    const fichier = new File(["nom;prenom"], "e.csv", { type: "text/csv" })
    fireEvent.change(screen.getByLabelText("Fichier :"), { target: { files: [fichier] } })
    fireEvent.click(screen.getByRole("button", { name: /Importer/ }))

    const alerte = await screen.findByText(/Rien n'a été importé : 2 lignes à corriger/)
    expect(alerte).toBeTruthy()
    const liste = within(alerte.closest("[role=alert]") as HTMLElement)
    expect(liste.getByText("Ligne 3 : E-mail invalide : x")).toBeTruthy()
    expect(liste.getByText("Ligne 5 : Promotion inconnue : Z")).toBeTruthy()
  })

  it("import réussi : nombre de comptes créés et téléchargement des mots de passe proposé", async () => {
    vi.mocked(importerEtudiants).mockResolvedValue({
      importe: true, nbCrees: 2, erreurs: [], comptes: [{ ligne: 2, email: "a@ept.sn", motDePasseProvisoire: "Aa11Bb22Cc33" }],
    })
    afficher()
    await screen.findByText("Prenom1 Nom1")
    fireEvent.change(screen.getByLabelText("Fichier :"), { target: { files: [new File(["x"], "e.csv")] } })
    fireEvent.click(screen.getByRole("button", { name: /Importer/ }))
    expect(await screen.findByText("2 étudiants importés.")).toBeTruthy()
    expect(screen.getByRole("button", { name: /Télécharger les mots de passe provisoires/ })).toBeTruthy()
  })
})
