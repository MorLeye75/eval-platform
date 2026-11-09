// @vitest-environment jsdom
import { act, cleanup, renderHook } from "@testing-library/react"
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useAutoSave, type SauvegardeConfirmee } from "@/hooks/useAutoSave"

type Donnees = Record<number, string>
interface Props {
  data: Donnees
  enabled: boolean
}

const CLE = "passation-copie-12"
const tampon = () => JSON.parse(localStorage.getItem(CLE) ?? "null")

/** Comme usePassation : désactivé pendant le chargement, puis copie du serveur marquée comme sauvegardée. */
function monter(onSave: (d: Donnees, v: number | null) => Promise<SauvegardeConfirmee | void>) {
  const vue = renderHook(({ data, enabled }: Props) => useAutoSave({ data, onSave, cleTampon: CLE, enabled }), {
    initialProps: { data: {}, enabled: false },
  })
  act(() => vue.result.current.marquerSauvegarde({ 1: "serveur" }, 3))
  vue.rerender({ data: { 1: "serveur" }, enabled: true })
  return vue
}

describe("useAutoSave : tampon local et fermeture de page (DS-02)", () => {
  beforeEach(() => {
    localStorage.clear()
  })

  // Sans globals Vitest, Testing Library ne démonte pas seul : les écouteurs d'un test fausseraient le suivant
  afterEach(() => {
    cleanup()
  })

  it("rien n'est écrit tant que les réponses sont celles du serveur", () => {
    monter(vi.fn())

    expect(tampon()).toBeNull()
  })

  it("chaque modification est écrite tout de suite dans le tampon (un F5 avant les 30 s ne perd rien)", () => {
    const { rerender } = monter(vi.fn())

    rerender({ data: { 1: "modifiée" }, enabled: true })

    expect(tampon()).toEqual({ version: 3, donnees: { 1: "modifiée" } })
  })

  it("annuler une modification (retour aux réponses du serveur) vide le tampon : un F5 ne la ressuscite pas", () => {
    const { rerender } = monter(vi.fn())
    rerender({ data: { 1: "cochée puis décochée" }, enabled: true })
    expect(tampon()).not.toBeNull()

    rerender({ data: { 1: "serveur" }, enabled: true })

    expect(tampon()).toBeNull()
  })

  it("une modification faite pendant l'envoi reste dans le tampon, sur la nouvelle version du serveur", async () => {
    let confirmer!: (c: SauvegardeConfirmee) => void
    const onSave = vi.fn(() => new Promise<SauvegardeConfirmee>((r) => (confirmer = r)))
    const { result, rerender } = monter(onSave)
    rerender({ data: { 1: "envoyée" }, enabled: true })

    let envoi!: Promise<unknown>
    act(() => {
      envoi = result.current.sauvegarderMaintenant()
    })
    await act(async () => {
      await Promise.resolve()
    })
    expect(onSave).toHaveBeenCalledWith({ 1: "envoyée" }, 3)
    rerender({ data: { 1: "tapée pendant l'envoi" }, enabled: true })
    await act(async () => {
      confirmer({ version: 4 })
      await envoi
    })

    expect(tampon()).toEqual({ version: 4, donnees: { 1: "tapée pendant l'envoi" } })
  })

  it("une sauvegarde confirmée sans modification depuis vide le tampon", async () => {
    const { result, rerender } = monter(vi.fn(async () => ({ version: 4 })))
    rerender({ data: { 1: "modifiée" }, enabled: true })

    await act(async () => {
      await result.current.sauvegarderMaintenant()
    })

    expect(tampon()).toBeNull()
  })

  it("échec d'envoi : le tampon garde les réponses les plus récentes, pas celles qui étaient envoyées", async () => {
    let echouer!: (e: Error) => void
    const onSave = vi.fn(() => new Promise<SauvegardeConfirmee>((_, rejeter) => (echouer = rejeter)))
    const { result, rerender } = monter(onSave)
    rerender({ data: { 1: "envoyée" }, enabled: true })

    let envoi!: Promise<unknown>
    act(() => {
      envoi = result.current.sauvegarderMaintenant()
    })
    await act(async () => {
      await Promise.resolve()
    })
    rerender({ data: { 1: "tapée pendant l'envoi" }, enabled: true })
    await act(async () => {
      echouer(new Error("réseau"))
      await envoi
    })

    expect(tampon()).toEqual({ version: 3, donnees: { 1: "tapée pendant l'envoi" } })
  })

  it("requête expirée (délai Axios) : échec, réponses gardées, et la file des envois n'est pas bloquée", async () => {
    const expiree = Object.assign(new Error("timeout of 15000ms exceeded"), { code: "ECONNABORTED", isAxiosError: true })
    const onSave = vi.fn<(d: Donnees, v: number | null) => Promise<SauvegardeConfirmee>>()
      .mockRejectedValueOnce(expiree)
      .mockResolvedValueOnce({ version: 4 })
    const { result, rerender } = monter(onSave)
    rerender({ data: { 1: "modifiée" }, enabled: true })

    let premier!: unknown
    await act(async () => {
      premier = await result.current.sauvegarderMaintenant()
    })
    expect(premier).toBe("echec")
    expect(tampon()).toEqual({ version: 3, donnees: { 1: "modifiée" } })
    expect(result.current.erreurSauvegarde).toBe(true)

    let second!: unknown
    await act(async () => {
      second = await result.current.sauvegarderMaintenant()
    })
    expect(second).toBe("ok")
    expect(tampon()).toBeNull()
  })

  it("409 passager (VERROU_INDISPONIBLE) : échec à réessayer, l'étudiant n'est pas renvoyé de l'épreuve", async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
    const verrou = new AxiosError("conflit", "409", config, null, {
      status: 409, statusText: "", headers: {}, config, data: { code: "VERROU_INDISPONIBLE" },
    })
    const onVerrouillage = vi.fn()
    const vue = renderHook(
      ({ data, enabled }: Props) =>
        useAutoSave({ data, onSave: vi.fn().mockRejectedValue(verrou), cleTampon: CLE, enabled, onVerrouillage }),
      { initialProps: { data: {}, enabled: false } }
    )
    act(() => vue.result.current.marquerSauvegarde({ 1: "serveur" }, 3))
    vue.rerender({ data: { 1: "modifiée" }, enabled: true })

    let resultat!: unknown
    await act(async () => {
      resultat = await vue.result.current.sauvegarderMaintenant()
    })

    expect(resultat).toBe("echec")
    expect(onVerrouillage).not.toHaveBeenCalled()
    expect(tampon()).toEqual({ version: 3, donnees: { 1: "modifiée" } })
  })

  it("fermer ou recharger la page avec des réponses non envoyées demande confirmation", () => {
    const { rerender } = monter(vi.fn())
    const fermeture = () => {
      const evt = new Event("beforeunload", { cancelable: true })
      window.dispatchEvent(evt)
      return evt.defaultPrevented
    }
    expect(fermeture()).toBe(false)

    rerender({ data: { 1: "modifiée" }, enabled: true })

    expect(fermeture()).toBe(true)
  })

  it("plus de demande de confirmation une fois la passation terminée (désactivé)", () => {
    const { rerender } = monter(vi.fn())
    rerender({ data: { 1: "modifiée" }, enabled: false })

    const evt = new Event("beforeunload", { cancelable: true })
    window.dispatchEvent(evt)

    expect(evt.defaultPrevented).toBe(false)
  })
})

describe("useAutoSave : sauvegarde enregistrée dont la réponse s'est perdue", () => {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  const reseau = () => new AxiosError("Network Error", "ERR_NETWORK", config) // pas de réponse : issue inconnue
  const obsolete = (versionActuelle: number) => new AxiosError("conflit", "409", config, null, {
    status: 409, statusText: "", headers: {}, config, data: { code: "VERSION_OBSOLETE", versionActuelle },
  })

  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    cleanup()
  })

  type Envoi = (d: Donnees, v: number | null) => Promise<SauvegardeConfirmee | void>
  type Verification = (envois: Donnees[]) => Promise<{ version: number; index: number } | null>

  function monterAvec(onSave: Envoi, verifierEnvoiPerdu: Verification, onConflit = vi.fn()) {
    const vue = renderHook(
      ({ data, enabled }: Props) =>
        useAutoSave({ data, onSave, cleTampon: CLE, enabled, onConflit, verifierEnvoiPerdu }),
      { initialProps: { data: {}, enabled: false } }
    )
    act(() => vue.result.current.marquerSauvegarde({ 1: "serveur" }, 3))
    return { vue, onConflit }
  }

  async function sauvegarder(vue: { result: { current: { sauvegarderMaintenant: () => Promise<unknown> } } }) {
    let resultat!: unknown
    await act(async () => {
      resultat = await vue.result.current.sauvegarderMaintenant()
    })
    return resultat
  }

  it("le serveur contient l'envoi perdu : pas de faux conflit, les réponses actuelles repartent sur sa version", async () => {
    const onSave = vi.fn<Envoi>()
      .mockRejectedValueOnce(reseau())        // enregistré par le serveur (version 4), réponse perdue
      .mockRejectedValueOnce(obsolete(4))     // envoi suivant sur la version 3 : refusé
      .mockResolvedValueOnce({ version: 5, dateSauvegarde: "2026-11-09T10:00:00" })
    const verifierEnvoiPerdu = vi.fn<Verification>().mockResolvedValue({ version: 4, index: 0 })
    const { vue, onConflit } = monterAvec(onSave, verifierEnvoiPerdu)

    vue.rerender({ data: { 1: "A" }, enabled: true })
    expect(await sauvegarder(vue)).toBe("echec")
    vue.rerender({ data: { 1: "AB" }, enabled: true })
    expect(await sauvegarder(vue)).toBe("ok")

    expect(verifierEnvoiPerdu).toHaveBeenCalledWith([{ 1: "A" }])
    expect(onSave).toHaveBeenLastCalledWith({ 1: "AB" }, 4)
    expect(onConflit).not.toHaveBeenCalled()
    expect(localStorage.getItem(CLE + "-conflit")).toBeNull() // rien de mis de côté
  })

  it("le serveur contient autre chose (autre onglet) : vrai conflit, comme avant", async () => {
    const onSave = vi.fn<Envoi>().mockRejectedValueOnce(reseau()).mockRejectedValueOnce(obsolete(4))
    const verifierEnvoiPerdu = vi.fn<Verification>().mockResolvedValue(null)
    const { vue, onConflit } = monterAvec(onSave, verifierEnvoiPerdu)

    vue.rerender({ data: { 1: "A" }, enabled: true })
    await sauvegarder(vue)
    vue.rerender({ data: { 1: "AB" }, enabled: true })

    expect(await sauvegarder(vue)).toBe("conflit")
    expect(onConflit).toHaveBeenCalledTimes(1)
  })

  it("409 sans envoi incertain avant : aucune vérification, conflit direct", async () => {
    const onSave = vi.fn<Envoi>().mockRejectedValueOnce(obsolete(4))
    const verifierEnvoiPerdu = vi.fn<Verification>()
    const { vue, onConflit } = monterAvec(onSave, verifierEnvoiPerdu)

    vue.rerender({ data: { 1: "A" }, enabled: true })

    expect(await sauvegarder(vue)).toBe("conflit")
    expect(verifierEnvoiPerdu).not.toHaveBeenCalled()
    expect(onConflit).toHaveBeenCalledTimes(1)
  })
})

