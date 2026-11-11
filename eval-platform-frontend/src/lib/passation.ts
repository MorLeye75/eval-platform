import { isAxiosError } from "axios"
import { api } from "@/lib/axios"
import type { CopieEnCours, ReponseItem, SauvegardeResponse } from "@/types"

// Appels de la passation : DS-01 (soumission) et DS-02 (auto-save)

/**
 * Tailles maximales d'une réponse, identiques à celles du serveur (ReponseItemDto) : au-delà, chaque sauvegarde
 * serait refusée (400) et la remise manuelle bloquée faute de « dernière sauvegarde » réussie.
 */
export const TAILLE_MAX_TEXTE = 20_000
export const TAILLE_MAX_CODE = 50_000

/** « 20 000 » : nombre lisible pour les messages de limite. */
export const formatNombre = (n: number) => n.toLocaleString("fr-FR").replace(/\s/g, " ")

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

/** DS-01 : POST /soumissions/{id}/soumettre. Renvoie l'heure de remise enregistrée par le serveur. */
export async function soumettreCopie(soumissionId: number) {
  const { data } = await api.post<{ dateRemise: string | null }>(`/soumissions/${soumissionId}/soumettre`)
  return data?.dateRemise ?? null
}

/** Quiz chronométré : passe à la question suivante si la copie est encore sur `indexActuel` (renvoie la copie). */
export async function questionQuizSuivante(soumissionId: number, indexActuel: number) {
  const { data } = await api.post<CopieEnCours>(`/soumissions/${soumissionId}/quiz/suivante`, { indexActuel })
  return data
}

/** Anti-triche : signale une sortie de la page ; renvoie le nombre de sorties enregistrées par le serveur. */
export async function signalerChangementOnglet(soumissionId: number) {
  const { data } = await api.post<{ nbChangementsOnglet: number }>(`/soumissions/${soumissionId}/changement-onglet`)
  return data.nbChangementsOnglet
}

function codeConflit(err: unknown): string | null {
  if (!isAxiosError(err) || err.response?.status !== 409) return null
  return typeof err.response.data?.code === "string" ? err.response.data.code : ""
}

/** Sauvegarde refusée : les réponses ont été enregistrées entre-temps depuis un autre onglet ou appareil. */
export function estVersionObsolete(err: unknown) {
  return codeConflit(err) === "VERSION_OBSOLETE"
}

/** Codes 409 qui figent définitivement la copie (l'épreuve est terminée pour l'étudiant). */
const CODES_COPIE_FIGEE = ["TEMPS_EXPIRE", "SOUMISSION_VERROUILLEE"]

/**
 * Le serveur a refusé l'action parce que la copie est figée : temps dépassé
 * au-delà de la marge, ou copie déjà soumise. Les autres 409 (conflit de version, conflit en base,
 * verrou momentanément indisponible, code inconnu) sont passagers : l'étudiant reste dans l'épreuve.
 * Un 409 sans code (serveur ancien) reste traité comme une copie figée.
 */
export function estCopieVerrouillee(err: unknown) {
  const code = codeConflit(err)
  return code !== null && (code === "" || CODES_COPIE_FIGEE.includes(code))
}

/**
 * Deux jeux de réponses sont-ils les mêmes, sur les questions données ? L'ordre des réponses et des choix cochés
 * ne compte pas. Sert à reconnaître une sauvegarde enregistrée par le serveur dont la réponse s'est perdue.
 */
export function memesReponses(a: ReponseItem[], b: ReponseItem[], questionIds: Set<number>) {
  const canonique = (reponses: ReponseItem[]) =>
    JSON.stringify(
      reponses
        .filter((r) => questionIds.has(r.questionId))
        .map((r) => ({
          questionId: r.questionId,
          choixIds: [...(r.choixIds ?? [])].sort((x, y) => x - y),
          texteSoumis: r.texteSoumis ?? null,
          codeSoumis: r.codeSoumis ?? null,
          langage: r.langage ?? null,
        }))
        .sort((x, y) => x.questionId - y.questionId)
    )
  return canonique(a) === canonique(b)
}

/** La requête n'a pas atteint le serveur (coupure réseau). */
export function estErreurReseau(err: unknown) {
  return isAxiosError(err) && !err.response
}

/** Statuts 4xx qui ne disent rien du contenu : session (401), délai (408), conflits (409), trop de requêtes (429). */
const STATUTS_A_REESSAYER = [401, 408, 409, 429]

/**
 * Le serveur a refusé la requête pour son contenu (400 validation, 404, 413, 422…) : la renvoyer telle quelle
 * échouerait encore. À distinguer d'une coupure réseau ou d'une panne du serveur (5xx), qui méritent un nouvel essai.
 */
export function estRequeteRefusee(err: unknown) {
  const status = isAxiosError(err) ? err.response?.status : undefined
  return status !== undefined && status >= 400 && status < 500 && !STATUTS_A_REESSAYER.includes(status)
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
