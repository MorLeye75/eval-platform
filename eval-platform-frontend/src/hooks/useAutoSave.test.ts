import { beforeEach, describe, expect, it, vi } from "vitest"
import { lireTampon, viderTampon } from "@/hooks/useAutoSave"

/** localStorage en mémoire (les tests tournent sous Node, sans navigateur). */
function stockageMemoire(): Storage {
  const valeurs = new Map<string, string>()
  return {
    get length() {
      return valeurs.size
    },
    clear: () => valeurs.clear(),
    getItem: (cle) => valeurs.get(cle) ?? null,
    key: (index) => [...valeurs.keys()][index] ?? null,
    removeItem: (cle) => {
      valeurs.delete(cle)
    },
    setItem: (cle, valeur) => {
      valeurs.set(cle, String(valeur))
    },
  }
}

const CLE = "passation-copie-12"
const tampon = (version: number | null, donnees: unknown) => JSON.stringify({ version, donnees })

describe("tampon localStorage de la copie (DS-02, DS-10)", () => {
  beforeEach(() => {
    vi.stubGlobal("localStorage", stockageMemoire())
  })

  it("restaure un tampon saisi sur la version actuelle du serveur", () => {
    localStorage.setItem(CLE, tampon(3, { 7: [2] }))

    expect(lireTampon(CLE, 3)).toEqual({ 7: [2] })
  })

  it("ne restaure pas un tampon saisi sur une version plus ancienne, mais le met de côté sans le détruire", () => {
    localStorage.setItem(CLE, tampon(2, { 7: [2] }))

    expect(lireTampon(CLE, 3)).toBeNull()
    expect(localStorage.getItem(CLE)).toBeNull()
    expect(JSON.parse(localStorage.getItem(`${CLE}-conflit`)!)).toEqual({ version: 2, donnees: { 7: [2] } })
  })

  it("un tampon saisi avant toute sauvegarde (version 0) est restauré si le serveur n'a rien reçu depuis", () => {
    localStorage.setItem(CLE, tampon(0, { 7: "brouillon" }))

    expect(lireTampon(CLE, 0)).toEqual({ 7: "brouillon" })
  })

  it("aucun tampon ou tampon illisible : rien à restaurer", () => {
    expect(lireTampon(CLE, 0)).toBeNull()

    localStorage.setItem(CLE, "{pas du json")
    expect(lireTampon(CLE, 0)).toBeNull()
  })

  it("viderTampon supprime le tampon et le tampon mis de côté (copie remise)", () => {
    localStorage.setItem(CLE, tampon(3, {}))
    localStorage.setItem(`${CLE}-conflit`, tampon(2, {}))

    viderTampon(CLE)

    expect(localStorage.getItem(CLE)).toBeNull()
    expect(localStorage.getItem(`${CLE}-conflit`)).toBeNull()
  })

  it("stockage indisponible (navigation privée) : aucune exception", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("indisponible")
      },
      removeItem: () => {
        throw new Error("indisponible")
      },
    })

    expect(lireTampon(CLE, 0)).toBeNull()
    expect(() => viderTampon(CLE)).not.toThrow()
  })
})
