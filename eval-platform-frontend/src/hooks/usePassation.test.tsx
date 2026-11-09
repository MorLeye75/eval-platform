// @vitest-environment jsdom
import type { ReactNode } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, cleanup, renderHook, waitFor } from "@testing-library/react"
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { usePassation } from "@/hooks/usePassation"
import { demarrerEpreuve, sauvegarderReponses, signalerChangementOnglet, soumettreCopie } from "@/lib/passation"
import type { CopieEnCours, QuestionExamen, ReponseItem } from "@/types"

const navigate = vi.fn()
vi.mock("react-router-dom", async (original) => ({
  ...(await original<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}))
vi.mock("@/lib/passation", async (original) => ({
  ...(await original<typeof import("@/lib/passation")>()),
  demarrerEpreuve: vi.fn(),
  sauvegarderReponses: vi.fn(),
  soumettreCopie: vi.fn(),
  signalerChangementOnglet: vi.fn(),
}))

const COPIE: CopieEnCours = {
  id: 12,
  evaluationId: 7,
  titreEvaluation: "Structures de données",
  descriptionEvaluation: null,
  statut: "EN_COURS",
  dateDebut: "2026-11-07T10:00:00",
  dateRemise: null,
  versionReponses: 0,
  dureeMinutes: 30,
  tempsRestantSecondes: 1800,
  nbChangementsOnglet: 0,
  questions: [],
}

const config = { url: "/soumissions/12/soumettre", headers: new AxiosHeaders() } as InternalAxiosRequestConfig
/** Délai Axios dépassé : la requête est partie, mais aucune réponse n'est revenue. */
const delaiDepasse = () => new AxiosError("timeout of 15000ms exceeded", "ECONNABORTED", config)
const conflit = (code: string) =>
  new AxiosError("conflit", "409", config, null, { status: 409, statusText: "", headers: {}, config, data: { code } })

// Fonctions stables, comme dans les pages de passation
const depuisCopie = () => ({})
const versReponses = () => []
const compterTraitees = () => 0

function monter() {
  const queryClient = new QueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return renderHook(() => usePassation({ evaluationId: "7", depuisCopie, versReponses, compterTraitees }), { wrapper })
}

describe("usePassation : soumission (DS-01)", () => {
  beforeEach(() => {
    localStorage.clear()
    navigate.mockReset()
    vi.mocked(demarrerEpreuve).mockResolvedValue(COPIE)
  })

  afterEach(() => {
    cleanup()
    vi.mocked(soumettreCopie).mockReset()
  })

  it("remise expirée côté navigateur mais reçue par le serveur : le réessai mène à la confirmation", async () => {
    // Le serveur est idempotent : une copie déjà remise renvoie 200 « Cette copie a déjà été transmise. »
    vi.mocked(soumettreCopie).mockRejectedValueOnce(delaiDepasse()).mockResolvedValueOnce("2026-11-07T10:42:13.51")
    const { result } = monter()
    await waitFor(() => expect(result.current.chargement).toBe(false))

    await act(async () => {
      await result.current.soumettre()
    })
    expect(navigate).not.toHaveBeenCalled()
    expect(result.current.erreurSoumission).toContain("soumission a échoué")
    expect(result.current.soumissionEnCours).toBe(false)

    await act(async () => {
      await result.current.soumettre()
    })
    // Heure de remise donnée par le serveur, pas celle du navigateur
    expect(navigate).toHaveBeenCalledWith(
      "/examen/7/confirmation",
      expect.objectContaining({ state: expect.objectContaining({ dateRemise: "2026-11-07T10:42:13.51" }) })
    )
    expect(sauvegarderReponses).not.toHaveBeenCalled() // rien de modifié : pas de sauvegarde avant la remise
  })

  it("temps dépassé au-delà de la marge (409 TEMPS_EXPIRE) : écran d'expiration", async () => {
    vi.mocked(soumettreCopie).mockRejectedValueOnce(conflit("TEMPS_EXPIRE"))
    const { result } = monter()
    await waitFor(() => expect(result.current.chargement).toBe(false))

    await act(async () => {
      await result.current.soumettre()
    })

    expect(navigate).toHaveBeenCalledWith("/examen/7/expiration", expect.anything())
  })

  it("anti-triche : le compteur repart de celui du serveur, et chaque sortie de la page lui est signalée", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue({ ...COPIE, nbChangementsOnglet: 2 })
    vi.mocked(signalerChangementOnglet).mockResolvedValue(3)
    const { result } = monter()
    await waitFor(() => expect(result.current.chargement).toBe(false))
    expect(result.current.changementsOnglet).toBe(2)
    expect(signalerChangementOnglet).not.toHaveBeenCalled()

    Object.defineProperty(document, "hidden", { configurable: true, get: () => true })
    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"))
    })
    Object.defineProperty(document, "hidden", { configurable: true, get: () => false })

    expect(signalerChangementOnglet).toHaveBeenCalledWith(12)
    await waitFor(() => expect(result.current.changementsOnglet).toBe(3)) // valeur du serveur
  })
})

describe("usePassation : rechargement après une sauvegarde dont la réponse s'est perdue (DS-02)", () => {
  type Textes = Record<number, string>
  const question = (texte: string | null): QuestionExamen => ({
    id: 5,
    enonce: "Expliquez le tas binaire",
    ordre: 1,
    points: 4,
    type: "OUVERTE",
    estChoixMultiple: null,
    choixPossibles: [],
    casDeTestsVisibles: [],
    reponseActuelle: texte === null ? null : { questionId: 5, texteSoumis: texte },
  })
  const depuisCopieTextes = (questions: QuestionExamen[]): Textes =>
    Object.fromEntries(questions.flatMap((q) => (q.reponseActuelle?.texteSoumis ? [[q.id, q.reponseActuelle.texteSoumis]] : [])))
  const versReponsesTextes = (r: Textes): ReponseItem[] =>
    Object.entries(r).map(([id, texte]) => ({ questionId: Number(id), texteSoumis: texte }))

  function monterTextes() {
    const queryClient = new QueryClient()
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    return renderHook(
      () =>
        usePassation<Textes>({
          evaluationId: "7",
          depuisCopie: depuisCopieTextes,
          versReponses: versReponsesTextes,
          compterTraitees: (r) => Object.keys(r).length,
        }),
      { wrapper }
    )
  }

  beforeEach(() => {
    localStorage.clear()
    vi.mocked(sauvegarderReponses).mockReset()
  })

  afterEach(() => cleanup())

  it("les réponses tapées après l'envoi perdu sont restaurées, puis envoyées sur la version du serveur", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue({ ...COPIE, questions: [question(null)] })
    // Le serveur enregistre la sauvegarde (version 0 → 1), mais sa réponse n'arrive jamais
    vi.mocked(sauvegarderReponses).mockRejectedValueOnce(delaiDepasse())
    const premier = monterTextes()
    await waitFor(() => expect(premier.result.current.chargement).toBe(false))

    act(() => premier.result.current.setReponses({ 5: "Un arbre" }))
    await act(async () => {
      await premier.result.current.sauvegarderMaintenant()
    })
    act(() => premier.result.current.setReponses({ 5: "Un arbre binaire presque complet" }))
    premier.unmount()

    // F5 : la copie du serveur contient l'envoi resté sans réponse, en version 1
    vi.mocked(demarrerEpreuve).mockResolvedValue({ ...COPIE, versionReponses: 1, questions: [question("Un arbre")] })
    vi.mocked(sauvegarderReponses).mockResolvedValue({ dateSauvegarde: "2026-11-07T10:05:00", versionReponses: 2 } as never)
    const second = monterTextes()
    await waitFor(() => expect(second.result.current.chargement).toBe(false))

    expect(second.result.current.reponses).toEqual({ 5: "Un arbre binaire presque complet" })
    await act(async () => {
      await second.result.current.sauvegarderMaintenant()
    })
    expect(sauvegarderReponses).toHaveBeenLastCalledWith(
      12,
      [{ questionId: 5, texteSoumis: "Un arbre binaire presque complet" }],
      1
    )
  })

  it("copie enregistrée ailleurs entre-temps (contenu différent) : les réponses du serveur font foi", async () => {
    vi.mocked(demarrerEpreuve).mockResolvedValue({ ...COPIE, questions: [question(null)] })
    vi.mocked(sauvegarderReponses).mockRejectedValueOnce(delaiDepasse())
    const premier = monterTextes()
    await waitFor(() => expect(premier.result.current.chargement).toBe(false))

    act(() => premier.result.current.setReponses({ 5: "Un arbre" }))
    await act(async () => {
      await premier.result.current.sauvegarderMaintenant()
    })
    act(() => premier.result.current.setReponses({ 5: "Un arbre binaire" }))
    premier.unmount()

    // Version 1 venue d'un autre appareil : ce n'est pas notre envoi
    vi.mocked(demarrerEpreuve).mockResolvedValue({ ...COPIE, versionReponses: 1, questions: [question("Autre appareil")] })
    const second = monterTextes()
    await waitFor(() => expect(second.result.current.chargement).toBe(false))

    expect(second.result.current.reponses).toEqual({ 5: "Autre appareil" })
    expect(localStorage.getItem("passation-copie-12-conflit")).not.toBeNull() // mis de côté, pas détruit
  })
})
