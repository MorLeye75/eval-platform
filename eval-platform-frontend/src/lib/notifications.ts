import { api } from "@/lib/axios"
import type { NotificationItem, PageNotifications, Role } from "@/types"

/** Taille d'une page de notifications (le serveur la limite à 50). */
export const TAILLE_PAGE_NOTIFICATIONS = 20

/** Fréquence de mise à jour du compteur de la cloche (DS-15 : pas de WebSocket). */
export const INTERVALLE_COMPTEUR_MS = 60_000

/** Clés de cache partagées par la page et le compteur : marquer comme lu met les deux à jour. */
export const CLE_NOTIFICATIONS = ["notifications"] as const
export const CLE_NON_LUES = ["notifications", "non-lues"] as const
export const CLE_LISTE_NOTIFICATIONS = ["notifications", "liste"] as const

/** DS-15 : mes notifications, les plus récentes d'abord. */
export async function listerNotifications(page: number, taille = TAILLE_PAGE_NOTIFICATIONS) {
  const { data } = await api.get<PageNotifications>("/notifications", { params: { page, taille } })
  return data
}

export async function nombreNonLues() {
  const { data } = await api.get<{ nombre: number }>("/notifications/non-lues/nombre")
  return data.nombre
}

export async function marquerNotificationLue(id: number) {
  await api.put(`/notifications/${id}/lire`)
}

export async function toutMarquerLu() {
  await api.put("/notifications/lire-tout")
}

/**
 * Page ouverte par un clic sur la notification, ou null si elle ne mène nulle part. Côté étudiant : les résultats
 * de sa copie (DS-13) ou le détail de l'évaluation. Les écrans enseignant et admin sont branchés par Aminou.
 */
export function destination(n: NotificationItem, role: Role | undefined): string | null {
  if (role !== "ETUDIANT") return null
  if (n.type === "RESULTAT_PUBLIE" && n.soumissionId !== null) return `/etudiant/resultats/${n.soumissionId}`
  if (n.type === "EVALUATION_OUVERTE" && n.evaluationId !== null) return `/etudiant/evaluations/${n.evaluationId}`
  return null
}
