// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios"
import { MemoryRouter } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"
import { LoginForm } from "@/components/login-form"

const login = vi.fn()
vi.mock("@/context/AuthContext", () => ({ useAuth: () => ({ login }) }))

const config = { url: "/auth/login", headers: new AxiosHeaders() } as InternalAxiosRequestConfig
function erreurHttp(status: number, data: unknown) {
  return new AxiosError("échec", String(status), config, null, { status, statusText: "", headers: {}, config, data })
}

async function seConnecter() {
  render(
    <MemoryRouter>
      <LoginForm />
    </MemoryRouter>
  )
  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "etudiant@ept.sn" } })
  fireEvent.change(screen.getByLabelText(/mot de passe/i), { target: { value: "mauvais" } })
  fireEvent.click(screen.getByRole("button", { name: /connexion|connecter/i }))
}

describe("LoginForm : messages d'erreur", () => {
  afterEach(() => {
    cleanup()
    login.mockReset()
  })

  it("trop de tentatives (429) : affiche le message du serveur avec le délai d'attente", async () => {
    login.mockRejectedValue(
      erreurHttp(429, {
        code: "TROP_DE_TENTATIVES",
        detail: "Trop de tentatives de connexion échouées. Réessayez dans 15 minute(s).",
      })
    )

    await seConnecter()

    expect(await screen.findByText(/Réessayez dans 15 minute\(s\)/)).toBeTruthy()
  })

  it("identifiants refusés (401) : message générique, sans dire si le compte existe", async () => {
    login.mockRejectedValue(erreurHttp(401, { detail: "Identifiants invalides ou compte désactivé." }))

    await seConnecter()

    expect(await screen.findByText("Email ou mot de passe incorrect.")).toBeTruthy()
  })
})
