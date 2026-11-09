// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest"
import { CLE_BROUILLON_EVALUATION, cleTamponCopie, viderSession } from "@/lib/session"

describe("viderSession (poste partagé)", () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    localStorage.setItem("token", "jeton")
    localStorage.setItem("user", JSON.stringify({ email: "prof@ept.sn", role: "ENSEIGNANT" }))
    localStorage.setItem(cleTamponCopie(12), JSON.stringify({ version: 3, donnees: { 7: [2] } }))
    localStorage.setItem(`${cleTamponCopie(12)}-conflit`, JSON.stringify({ version: 2, donnees: {} }))
    localStorage.setItem("theme", "dark")
    // Le brouillon enseignant contient les bonnes réponses (estCorrect) et les sorties attendues
    sessionStorage.setItem(CLE_BROUILLON_EVALUATION, JSON.stringify({ questions: [{ choix: [{ estCorrect: true }] }] }))
  })

  it("efface jeton, utilisateur, brouillon et sauvegardes locales des copies, sans toucher au reste", () => {
    viderSession()

    expect(localStorage.getItem("token")).toBeNull()
    expect(localStorage.getItem("user")).toBeNull()
    expect(sessionStorage.getItem(CLE_BROUILLON_EVALUATION)).toBeNull()
    expect(localStorage.getItem(cleTamponCopie(12))).toBeNull()
    expect(localStorage.getItem(`${cleTamponCopie(12)}-conflit`)).toBeNull()
    expect(localStorage.getItem("theme")).toBe("dark")
  })

  it("peut garder les sauvegardes locales des copies (réponses pas encore envoyées)", () => {
    viderSession({ garderTamponsCopies: true })

    expect(localStorage.getItem("token")).toBeNull()
    expect(sessionStorage.getItem(CLE_BROUILLON_EVALUATION)).toBeNull()
    expect(localStorage.getItem(cleTamponCopie(12))).not.toBeNull()
    expect(localStorage.getItem(`${cleTamponCopie(12)}-conflit`)).not.toBeNull()
  })
})
