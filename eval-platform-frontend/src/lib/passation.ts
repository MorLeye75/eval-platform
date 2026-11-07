import { isAxiosError } from "axios"
import { api } from "@/lib/axios"
import type { CopieEnCours, ReponseItem, SauvegardeResponse } from "@/types"

// Appels de la passation : DS-01 (soumission) et DS-02 (auto-save)

/** Démarre la copie, ou reprend celle qui existe déjà (questions et réponses enregistrées). */
export async function demarrerEpreuve(evaluationId: string) {
  const { data } = await api.post<CopieEnCours>(
    `/soumissions/evaluations/${evaluationId}/demarrer`
  )
  return data
}

/** DS-02 : PUT /soumissions/{id}/sauvegarder (contenu + version sur laquelle il a été saisi) */
export async function sauvegarderReponses(
  soumissionId: number,
  reponses: ReponseItem[],
  versionAttendue: number | null
) {
  const { data } = await api.put<SauvegardeResponse>(
    `/soumissions/${soumissionId}/sauvegarder`,
    { reponses, versionAttendue }
  )
  return data
}

/** DS-01 : POST /soumissions/{id}/soumettre */
export async function soumettreCopie(soumissionId: number) {
  await api.post(`/soumissions/${soumissionId}/soumettre`)
}

function codeConflit(err: unknown): string | null {
  if (!isAxiosError(err) || err.response?.status !== 409) return null
  return typeof err.response.data?.code === "string" ? err.response.data.code : ""
}

/** Sauvegarde refusée : les réponses ont été enregistrées entre-temps depuis un autre onglet ou appareil. */
export function estVersionObsolete(err: unknown) {
  return codeConflit(err) === "VERSION_OBSOLETE"
}

/**
 * Le serveur a refusé l'action parce que la copie est figée : temps dépassé
 * au-delà de la marge, ou copie déjà soumise (409 Conflict hors conflit de version).
 */
export function estCopieVerrouillee(err: unknown) {
  const code = codeConflit(err)
  return code !== null && code !== "VERSION_OBSOLETE"
}

/** La requête n'a pas atteint le serveur (coupure réseau). */
export function estErreurReseau(err: unknown) {
  return isAxiosError(err) && !err.response
}

/** Message d'erreur renvoyé par le serveur (ProblemDetail RFC 7807), sinon message par défaut. */
export function messageErreur(err: unknown, parDefaut: string) {
  if (isAxiosError(err) && typeof err.response?.data?.detail === "string") {
    return err.response.data.detail as string
  }
  return parDefaut
}

/**
 * Heure HH:MM d'une date du serveur (LocalDateTime "2026-10-25T14:01:39.7098715"),
 * lue telle quelle : pas de fuseau ajouté et pas de dépendance au parseur de Date du navigateur.
 */
export function formatHeure(iso?: string) {
  if (iso && /T\d{2}:\d{2}/.test(iso)) return iso.slice(11, 16)
  return new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })
}

/** Numéros (1, 2…) des questions sans réponse, dans l'ordre de l'épreuve (pas leurs identifiants). */
export function numerosSansReponse(questions: { id: number }[], estTraitee: (questionId: number) => boolean) {
  return questions.flatMap((q, i) => (estTraitee(q.id) ? [] : [i + 1]))
}

/** Phrase de la fenêtre de confirmation listant les questions sans réponse, ou null si tout est traité. */
export function messageSansReponse(numeros: number[]): string | null {
  if (numeros.length === 0) return null
  if (numeros.length === 1) return `La question ${numeros[0]} est sans réponse et sera comptée comme non traitée.`
  const liste = `${numeros.slice(0, -1).join(", ")} et ${numeros[numeros.length - 1]}`
  return `Les questions ${liste} sont sans réponse et seront comptées comme non traitées.`
}
