// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest"
import { api } from "@/lib/axios"
import { changerMotDePasse } from "@/lib/profil"

vi.mock("@/lib/axios", () => ({ api: { get: vi.fn(), put: vi.fn() } }))

describe("changement de son mot de passe", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it("le jeton renvoyé remplace l'ancien : le serveur a révoqué les jetons émis avant le changement", async () => {
    localStorage.setItem("token", "ancien")
    vi.mocked(api.put).mockResolvedValue({ data: { token: "nouveau" } })

    await changerMotDePasse("actuel", "nouveauMotDePasse")

    expect(api.put).toHaveBeenCalledWith("/profil/mot-de-passe", { motDePasseActuel: "actuel", nouveauMotDePasse: "nouveauMotDePasse" })
    expect(localStorage.getItem("token")).toBe("nouveau")
  })

  it("refus du serveur : l'ancien jeton est conservé", async () => {
    localStorage.setItem("token", "ancien")
    vi.mocked(api.put).mockRejectedValue(new Error("400"))

    await expect(changerMotDePasse("faux", "nouveauMotDePasse")).rejects.toThrow()
    expect(localStorage.getItem("token")).toBe("ancien")
  })
})
