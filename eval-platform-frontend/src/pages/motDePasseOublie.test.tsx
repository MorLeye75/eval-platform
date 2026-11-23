// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { AxiosError, type AxiosResponse } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { demanderReinitialisation, reinitialiserMotDePasse } from "@/lib/motDePasseOublie"
import ForgotPasswordPage from "./ForgotPasswordPage"
import ResetPasswordPage from "./ResetPasswordPage"

vi.mock("@/lib/motDePasseOublie", () => ({ demanderReinitialisation: vi.fn(), reinitialiserMotDePasse: vi.fn() }))

function erreurHttp(status: number, detail: string, code?: string) {
  const response = { status, data: { detail, code }, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

function afficherDemande() {
  render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>)
}

function afficherReinitialisation(chemin = "/reset-password/jeton-recu") {
  render(
    <MemoryRouter initialEntries={[chemin]}>
      <Routes>
        <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/login" element={<p>Page de connexion</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

const boutonModifier = () => screen.getByRole("button", { name: /Modifier le mot de passe/ }) as HTMLButtonElement

function saisirMotDePasse(valeur: string, confirmation = valeur) {
  fireEvent.change(screen.getByLabelText("Mot de passe"), { target: { value: valeur } })
  fireEvent.change(screen.getByLabelText(/Confirmer/), { target: { value: confirmation } })
  fireEvent.click(boutonModifier())
}

describe("mot de passe oublié : demande du lien", () => {
  beforeEach(() => vi.mocked(demanderReinitialisation).mockReset().mockResolvedValue(undefined))
  afterEach(cleanup)

  it("envoie l'adresse au serveur puis affiche la confirmation (même réponse que le compte existe ou non)", async () => {
    afficherDemande()
    fireEvent.change(screen.getByLabelText(/e-?mail/i), { target: { value: "awa@ept.sn" } })
    fireEvent.click(screen.getByRole("button", { name: /Envoyer/ }))

    expect(await screen.findByText("Vérifiez votre boîte mail")).toBeTruthy()
    expect(demanderReinitialisation).toHaveBeenCalledWith("awa@ept.sn")
  })

  it("adresse vide : message, aucun appel", async () => {
    afficherDemande()
    fireEvent.click(screen.getByRole("button", { name: /Envoyer/ }))
    expect(await screen.findByText(/saisir votre adresse/)).toBeTruthy()
    expect(demanderReinitialisation).not.toHaveBeenCalled()
  })

  it("refus du serveur (adresse mal formée) : son message est affiché, pas de confirmation", async () => {
    vi.mocked(demanderReinitialisation).mockRejectedValueOnce(erreurHttp(400, "L'adresse e-mail n'est pas valide"))
    afficherDemande()
    fireEvent.change(screen.getByLabelText(/e-?mail/i), { target: { value: "awa" } })
    fireEvent.click(screen.getByRole("button", { name: /Envoyer/ }))

    expect(await screen.findByText("L'adresse e-mail n'est pas valide")).toBeTruthy()
    expect(screen.queryByText("Vérifiez votre boîte mail")).toBeNull()
  })
})

describe("mot de passe oublié : nouveau mot de passe depuis le lien", () => {
  beforeEach(() => vi.mocked(reinitialiserMotDePasse).mockReset().mockResolvedValue(undefined))
  afterEach(cleanup)

  it("envoie le jeton du lien et le nouveau mot de passe, puis confirme", async () => {
    afficherReinitialisation()
    saisirMotDePasse("NouveauMotDePasse1")

    expect(await screen.findByText("Mot de passe modifié")).toBeTruthy()
    expect(reinitialiserMotDePasse).toHaveBeenCalledWith("jeton-recu", "NouveauMotDePasse1")
  })

  it("lien invalide ou expiré (JETON_INVALIDE) : message du serveur et lien vers une nouvelle demande", async () => {
    vi.mocked(reinitialiserMotDePasse).mockRejectedValueOnce(
      erreurHttp(400, "Ce lien de réinitialisation est invalide ou a expiré. Faites une nouvelle demande.", "JETON_INVALIDE"))
    afficherReinitialisation()
    saisirMotDePasse("NouveauMotDePasse1")

    expect(await screen.findByText(/invalide ou a expiré/)).toBeTruthy()
    expect(screen.getByRole("link", { name: /nouvelle demande/i }).getAttribute("href")).toBe("/forgot-password")
  })

  it("mots de passe différents ou trop faibles : bouton désactivé, aucun appel au serveur", async () => {
    afficherReinitialisation()
    saisirMotDePasse("NouveauMotDePasse1", "Autre1234")
    expect(boutonModifier().disabled).toBe(true)
    saisirMotDePasse("court")
    expect(boutonModifier().disabled).toBe(true)
    await waitFor(() => expect(reinitialiserMotDePasse).not.toHaveBeenCalled())
  })

  it("lien sans jeton : page « Lien invalide », pas de formulaire", () => {
    afficherReinitialisation("/reset-password")
    expect(screen.getByText("Lien invalide")).toBeTruthy()
    expect(screen.queryByLabelText("Mot de passe")).toBeNull()
  })
})
