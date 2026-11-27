import { beforeEach, describe, expect, it, vi } from "vitest"
import { api } from "@/lib/axios"
import {
  changerEtatCompte,
  creerCompte,
  creerPromotion,
  csvMotsDePasse,
  importerEtudiants,
  listerComptes,
  modifierCompte,
  reinitialiserMotDePasse,
  renommerPromotion,
  supprimerPromotion,
} from "@/lib/adminComptes"

vi.mock("@/lib/axios", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

const COMPTE = { nom: "Sy", prenom: "Ali", email: "ali@ept.sn", promotionId: 1, numEtudiant: "N-1", matricule: null }

describe("appels d'administration des comptes (DS-16)", () => {
  beforeEach(() => vi.clearAllMocks())

  it("liste, création, modification : bonnes routes et corps", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })
    vi.mocked(api.post).mockResolvedValue({ data: { utilisateur: { id: 1 }, motDePasseProvisoire: "abc" } })
    vi.mocked(api.put).mockResolvedValue({ data: { id: 1 } })

    await listerComptes()
    expect(api.get).toHaveBeenCalledWith("/admin/utilisateurs")
    expect((await creerCompte({ ...COMPTE, role: "ETUDIANT" })).motDePasseProvisoire).toBe("abc")
    expect(api.post).toHaveBeenCalledWith("/admin/utilisateurs", { ...COMPTE, role: "ETUDIANT" })
    await modifierCompte(1, COMPTE)
    expect(api.put).toHaveBeenCalledWith("/admin/utilisateurs/1", COMPTE)
  })

  it("désactiver, réactiver, réinitialiser, créer une promotion", async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { motDePasseProvisoire: "xyz", id: 2, nom: "DIC3", annee: "2026" } })

    await changerEtatCompte(5, false)
    expect(api.post).toHaveBeenLastCalledWith("/admin/utilisateurs/5/desactiver")
    await changerEtatCompte(5, true)
    expect(api.post).toHaveBeenLastCalledWith("/admin/utilisateurs/5/activer")
    expect((await reinitialiserMotDePasse(5)).motDePasseProvisoire).toBe("xyz")
    expect(api.post).toHaveBeenLastCalledWith("/admin/utilisateurs/5/reinitialiser-mot-de-passe")
    await creerPromotion("DIC3", "2026")
    expect(api.post).toHaveBeenLastCalledWith("/admin/promotions", { nom: "DIC3", annee: "2026" })
  })

  it("renommer et supprimer une promotion : bonnes routes", async () => {
    vi.mocked(api.put).mockResolvedValue({ data: { id: 4, nom: "DIC3", annee: "2027" } })
    vi.mocked(api.delete).mockResolvedValue({ data: undefined })

    expect((await renommerPromotion(4, "DIC3", "2027")).nom).toBe("DIC3")
    expect(api.put).toHaveBeenCalledWith("/admin/promotions/4", { nom: "DIC3", annee: "2027" })
    await supprimerPromotion(4)
    expect(api.delete).toHaveBeenCalledWith("/admin/promotions/4")
  })

  it("import : fichier envoyé en multipart ; le 422 (lignes en erreur) est rendu, pas levé", async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { importe: false, nbCrees: 0, erreurs: [{ ligne: 3, message: "x" }], comptes: [] } })
    const fichier = new File(["a"], "e.csv", { type: "text/csv" })

    const r = await importerEtudiants(fichier)

    expect(r.importe).toBe(false)
    const [url, corps, options] = vi.mocked(api.post).mock.calls[0]
    expect(url).toBe("/admin/etudiants/import")
    expect((corps as FormData).get("fichier")).toBe(fichier)
    const accepte = (options as { validateStatus: (s: number) => boolean }).validateStatus
    expect([accepte(200), accepte(422), accepte(400), accepte(500)]).toEqual([true, true, false, false])
  })

  it("fichier des mots de passe : en-tête, une ligne par compte, cellules neutralisées contre les formules de tableur", () => {
    const csv = csvMotsDePasse([
      { ligne: 2, email: "a@ept.sn", motDePasseProvisoire: "Abc234efgh2k" },
      { ligne: 3, email: "=cmd@ept.sn", motDePasseProvisoire: "+Abc;\"x" },
    ])
    expect(csv.split("\n")).toEqual([
      "email;motDePasseProvisoire",
      "a@ept.sn;Abc234efgh2k",
      "'=cmd@ept.sn;\"'+Abc;\"\"x\"",
    ])
  })
})
