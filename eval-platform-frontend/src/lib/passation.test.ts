import { describe, expect, it } from "vitest"
import { AxiosError, type AxiosResponse } from "axios"
import {
  estCopieVerrouillee,
  estErreurReseau,
  estRequeteRefusee,
  estVersionObsolete,
  formatHeure,
  memesReponses,
  messageErreur,
} from "@/lib/passation"

/** Erreur axios telle que reçue pour une réponse HTTP du serveur. */
function erreurHttp(status: number, data: unknown = {}) {
  const response = { status, data, statusText: "", headers: {}, config: {} } as unknown as AxiosResponse
  return new AxiosError("Erreur HTTP", "ERR_BAD_REQUEST", undefined, undefined, response)
}

/** Erreur axios sans réponse : la requête n'a pas atteint le serveur. */
const erreurReseau = () => new AxiosError("Network Error", "ERR_NETWORK")

describe("lecture des 409 (DS-01, DS-02)", () => {
  it("VERSION_OBSOLETE est un conflit de version, pas une copie verrouillée", () => {
    const err = erreurHttp(409, { code: "VERSION_OBSOLETE", versionActuelle: 4 })
    expect(estVersionObsolete(err)).toBe(true)
    expect(estCopieVerrouillee(err)).toBe(false)
  })

  it.each(["TEMPS_EXPIRE", "SOUMISSION_VERROUILLEE"])("%s verrouille la copie", (code) => {
    const err = erreurHttp(409, { code })
    expect(estCopieVerrouillee(err)).toBe(true)
    expect(estVersionObsolete(err)).toBe(false)
  })

  it("un 409 sans code est traité comme une copie verrouillée", () => {
    expect(estCopieVerrouillee(erreurHttp(409))).toBe(true)
  })

  it.each(["CONFLIT_DONNEES", "VERROU_INDISPONIBLE", "CODE_FUTUR_INCONNU"])(
    "%s (conflit passager) ne verrouille pas la copie : l'étudiant n'est pas renvoyé de l'épreuve",
    (code) => {
      expect(estCopieVerrouillee(erreurHttp(409, { code }))).toBe(false)
    }
  )

  it("les autres erreurs ne sont ni un verrouillage ni un conflit", () => {
    for (const err of [erreurHttp(400), erreurHttp(500), erreurReseau(), new Error("autre")]) {
      expect(estCopieVerrouillee(err)).toBe(false)
      expect(estVersionObsolete(err)).toBe(false)
    }
  })
})

describe("estErreurReseau", () => {
  it("vrai seulement quand le serveur n'a pas répondu", () => {
    expect(estErreurReseau(erreurReseau())).toBe(true)
    expect(estErreurReseau(erreurHttp(500))).toBe(false)
  })
})

describe("estRequeteRefusee", () => {
  it("requête refusée pour son contenu (400, 403, 404, 413, 422) : la renvoyer telle quelle échouerait encore", () => {
    for (const status of [400, 403, 404, 413, 422]) expect(estRequeteRefusee(erreurHttp(status))).toBe(true)
  })

  it("pas un refus du contenu : réseau, 5xx, 401 (session), 408 et 429 (réessayer), 409 (traités à part)", () => {
    expect(estRequeteRefusee(erreurReseau())).toBe(false)
    for (const status of [401, 408, 409, 429, 500, 503]) expect(estRequeteRefusee(erreurHttp(status))).toBe(false)
  })
})

describe("messageErreur", () => {
  it("reprend le champ detail du ProblemDetail (RFC 7807)", () => {
    const err = erreurHttp(400, { detail: "Cette évaluation n'est pas encore ouverte." })
    expect(messageErreur(err, "défaut")).toBe("Cette évaluation n'est pas encore ouverte.")
  })

  it("sinon, renvoie le message par défaut", () => {
    expect(messageErreur(erreurReseau(), "défaut")).toBe("défaut")
    expect(messageErreur(erreurHttp(500, "texte brut"), "défaut")).toBe("défaut")
  })
})

describe("formatHeure", () => {
  it("lit l'heure d'une date serveur telle quelle, même avec 7 décimales", () => {
    expect(formatHeure("2026-10-25T14:01:39.7098715")).toBe("14:01")
    expect(formatHeure("2026-10-25T09:05:00")).toBe("09:05")
  })

  it("sans date serveur, renvoie l'heure locale au format HH:MM", () => {
    expect(formatHeure()).toMatch(/^\d{2}:\d{2}$/)
  })
})

describe("memesReponses : le serveur contient-il l'envoi resté sans réponse ?", () => {
  const ids = new Set([1, 2])

  it("mêmes réponses, choix cochés dans un autre ordre : identiques", () => {
    expect(memesReponses(
      [{ questionId: 2, choixIds: [7, 5] }, { questionId: 1, texteSoumis: "Pile" }],
      [{ questionId: 1, texteSoumis: "Pile" }, { questionId: 2, choixIds: [5, 7] }],
      ids
    )).toBe(true)
  })

  it("un texte, un choix ou un langage différent : pas identiques", () => {
    expect(memesReponses([{ questionId: 1, texteSoumis: "Pile" }], [{ questionId: 1, texteSoumis: "File" }], ids)).toBe(false)
    expect(memesReponses([{ questionId: 2, choixIds: [5] }], [{ questionId: 2, choixIds: [5, 7] }], ids)).toBe(false)
    expect(memesReponses(
      [{ questionId: 1, codeSoumis: "x", langage: "c" }],
      [{ questionId: 1, codeSoumis: "x", langage: "python" }],
      ids
    )).toBe(false)
  })

  it("seules les questions renvoyées par le serveur comptent (quiz : la question en cours)", () => {
    expect(memesReponses(
      [{ questionId: 1, choixIds: [3] }, { questionId: 9, choixIds: [4] }],
      [{ questionId: 1, choixIds: [3] }],
      ids
    )).toBe(true)
  })
})

