// @vitest-environment jsdom
import type { ReactNode } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, cleanup, renderHook, waitFor } from "@testing-library/react"
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { usePassation } from "@/hooks/usePassation"
import { demarrerEpreuve, sauvegarderReponses, soumettreCopie } from "@/lib/passation"
import type { CopieEnCours } from "@/types"

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
})
