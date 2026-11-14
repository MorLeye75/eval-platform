import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Bell, CheckCheck, Award, ClipboardList, CheckCircle2 } from "lucide-react"
import { useAuth } from "@/context/AuthContext"
import {
  CLE_LISTE_NOTIFICATIONS,
  CLE_NON_LUES,
  CLE_NOTIFICATIONS,
  destination,
  listerNotifications,
  marquerNotificationLue,
  nombreNonLues,
  toutMarquerLu,
} from "@/lib/notifications"
import type { NotificationItem, TypeNotification } from "@/types"

const TYPE_CONFIG: Record<TypeNotification, { icon: React.ElementType; cls: string; label: string }> = {
  RESULTAT_PUBLIE:    { icon: Award,         cls: "bg-primary/10 text-primary",                                    label: "Résultat"   },
  EVALUATION_OUVERTE: { icon: ClipboardList, cls: "bg-blue-500/10 text-blue-700 dark:text-blue-400",              label: "Évaluation" },
  CORRECTION_VALIDEE: { icon: CheckCircle2,  cls: "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400",        label: "Correction" },
}

function formatDate(iso: string) {
  const d = new Date(iso)
  const now = new Date()
  const diffH = Math.floor((now.getTime() - d.getTime()) / 3600000)
  if (diffH < 1)  return "À l'instant"
  if (diffH < 24) return `Il y a ${diffH}h`
  const diffJ = Math.floor(diffH / 24)
  if (diffJ < 7)  return `Il y a ${diffJ} j`
  return d.toLocaleDateString("fr-FR", { day: "numeric", month: "short" })
}

/**
 * Notifications de l'utilisateur connecté (DS-15), page partagée par l'étudiant et l'enseignant. Un clic marque
 * la notification comme lue et, côté étudiant, ouvre la page concernée (résultats de la copie, évaluation).
 */
export default function NotificationsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState("toutes")
  const [toutEnCours, setToutEnCours] = useState(false)

  const liste = useInfiniteQuery({
    queryKey: CLE_LISTE_NOTIFICATIONS,
    queryFn: ({ pageParam }) => listerNotifications(pageParam),
    initialPageParam: 0,
    getNextPageParam: (derniere) =>
      (derniere.page + 1) * derniere.taille < derniere.total ? derniere.page + 1 : undefined,
  })
  const { data: nonLues = 0 } = useQuery({ queryKey: CLE_NON_LUES, queryFn: nombreNonLues })

  const notifs = liste.data?.pages.flatMap((p) => p.notifications) ?? []
  const filtered = notifs.filter((n) => (tab === "toutes" ? true : !n.lue))

  function rafraichir() {
    return queryClient.invalidateQueries({ queryKey: CLE_NOTIFICATIONS })
  }

  async function ouvrir(n: NotificationItem) {
    if (!n.lue) {
      try {
        await marquerNotificationLue(n.id)
      } catch {
        // Hors ligne : la notification reste non lue, la page s'ouvre quand même
      }
      void rafraichir()
    }
    const cible = destination(n, user?.role)
    if (cible) navigate(cible)
  }

  async function toutLire() {
    setToutEnCours(true)
    try {
      await toutMarquerLu()
      await rafraichir()
    } finally {
      setToutEnCours(false)
    }
  }

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">

      {/* En-tête */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-3xl font-extrabold tracking-tight">
            Notifications
            {nonLues > 0 && (
              <span className="flex size-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
                {nonLues}
              </span>
            )}
          </h1>
          <p className="mt-1 text-muted-foreground">
            {nonLues === 0 ? "Tout est lu." : `${nonLues} notification${nonLues > 1 ? "s" : ""} non lue${nonLues > 1 ? "s" : ""}.`}
          </p>
        </div>
        {nonLues > 0 && (
          <Button variant="outline" className="rounded-xl" onClick={toutLire} disabled={toutEnCours}>
            <CheckCheck className="size-4" /> Tout marquer comme lu
          </Button>
        )}
      </div>

      {/* Onglets */}
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="rounded-xl">
          <TabsTrigger value="toutes">Toutes</TabsTrigger>
          <TabsTrigger value="non_lues">
            Non lues {nonLues > 0 && `(${nonLues})`}
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Liste */}
      {liste.isPending ? (
        <p className="py-16 text-center text-sm text-muted-foreground">Chargement des notifications...</p>
      ) : liste.isError ? (
        <p role="alert" className="py-16 text-center text-sm text-destructive">
          Impossible de charger vos notifications. Vérifiez votre connexion puis rechargez la page.
        </p>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-border py-16 text-center text-muted-foreground">
          <Bell className="size-8" />
          <p className="font-medium">Aucune notification.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {filtered.map(n => {
            const cfg = TYPE_CONFIG[n.type]
            return (
              <Card
                key={n.id}
                role="button"
                tabIndex={0}
                className={[
                  "cursor-pointer rounded-2xl border-border/60 shadow-sm transition-colors hover:bg-muted/30",
                  !n.lue ? "border-l-4 border-l-primary" : "",
                ].join(" ")}
                onClick={() => void ouvrir(n)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault()
                    void ouvrir(n)
                  }
                }}
              >
                <CardContent className="flex items-start gap-3 p-4">
                  {/* Icône type */}
                  <span className={`mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-[10px] ${cfg.cls}`}>
                    <cfg.icon className="size-[18px]" />
                  </span>

                  {/* Contenu */}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <Badge
                        variant="outline"
                        className={`rounded-full text-[11px] font-semibold ${cfg.cls}`}
                      >
                        {cfg.label}
                      </Badge>
                      <span className="text-[11px] text-muted-foreground">
                        {formatDate(n.dateEnvoi)}
                      </span>
                    </div>
                    <p className={`mt-1 text-sm leading-relaxed ${!n.lue ? "font-medium text-foreground" : "text-muted-foreground"}`}>
                      {n.message}
                    </p>
                  </div>

                  {/* Indicateur non lue */}
                  {!n.lue && (
                    <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" aria-label="Non lue" />
                  )}
                </CardContent>
              </Card>
            )
          })}
          {liste.hasNextPage && (
            <Button
              variant="outline"
              className="self-center rounded-xl"
              onClick={() => void liste.fetchNextPage()}
              disabled={liste.isFetchingNextPage}
            >
              {liste.isFetchingNextPage ? "Chargement..." : "Afficher plus"}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
