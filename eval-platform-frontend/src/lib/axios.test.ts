// @vitest-environment jsdom
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { api, DELAI_MAX_REQUETE_MS, gererErreurReponse } from "@/lib/axios"
import { CLE_BROUILLON_EVALUATION, cleTamponCopie } from "@/lib/session"

function erreur(status: number, url: string) {
  const config = { url, headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError("échec", String(status), config, null, {
    status,
    statusText: "",
    headers: {},
    config,
    data: null,
  })
}

describe("client HTTP", () => {
  it("une requête figée échoue au bout de 15 s au lieu d'attendre indéfiniment", () => {
    expect(DELAI_MAX_REQUETE_MS).toBe(15_000)
    expect(api.defaults.timeout).toBe(DELAI_MAX_REQUETE_MS)
  })
})

describe("intercepteur 401", () => {
  const redirection = vi.fn()

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    localStorage.setItem("token", "jeton")
    localStorage.setItem("user", "{}")
    localStorage.setItem(cleTamponCopie(5), "{}")
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, "{}")
    redirection.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("jeton refusé : vide la session (brouillon compris), garde les réponses non envoyées et renvoie au login", async () => {
    await expect(gererErreurReponse(erreur(401, "/soumissions/5"), redirection)).rejects.toBeInstanceOf(AxiosError)

    expect(localStorage.getItem("token")).toBeNull()
    expect(localStorage.getItem("user")).toBeNull()
    expect(sessionStorage.getItem(CLE_BROUILLON_EVALUATION)).toBeNull()
    expect(localStorage.getItem(cleTamponCopie(5))).not.toBeNull()
    expect(redirection).toHaveBeenCalledWith("/login")
  })

  it("mauvais identifiants sur /auth/login : rien n'est vidé, pas de rechargement", async () => {
    await expect(gererErreurReponse(erreur(401, "/auth/login"), redirection)).rejects.toBeInstanceOf(AxiosError)

    expect(localStorage.getItem("token")).toBe("jeton")
    expect(redirection).not.toHaveBeenCalled()
  })

  it("autre erreur (500) : session intacte", async () => {
    await expect(gererErreurReponse(erreur(500, "/soumissions/5"), redirection)).rejects.toBeInstanceOf(AxiosError)

    expect(localStorage.getItem("token")).toBe("jeton")
    expect(redirection).not.toHaveBeenCalled()
  })
})
