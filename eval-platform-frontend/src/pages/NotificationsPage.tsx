import { useState } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Bell, CheckCheck, Award, ClipboardList, CheckCircle2 } from "lucide-react"
import { useAuth } from "@/context/AuthContext"
import type { Role, TypeNotification } from "@/types"

// ============================================================
// Page partagée par l'étudiant et l'enseignant.
// Données mockées (par rôle) — à remplacer par :
// const { data } = useQuery({
//   queryKey: ["notifications"],
//   queryFn: () => api.get("/notifications")
// })
// Marquer lue : PUT /api/notifications/{id}/lire
// Tout marquer : PUT /api/notifications/lire-tout
// ============================================================

interface NotificationItem {
  id: number
  type: TypeNotification
  message: string
  lue: boolean
  dateEnvoi: string
}

const MOCK_ETUDIANT: NotificationItem[] = [
  { id: 1, type: "RESULTAT_PUBLIE",    message: "Les résultats de \"Structures de données — QCM\" ont été publiés. Votre note : 15 / 20.",             lue: false, dateEnvoi: "2026-07-13T10:00:00" },
  { id: 2, type: "EVALUATION_OUVERTE", message: "L'évaluation \"Algorithmique — exercice de code\" est maintenant disponible jusqu'au 15 juil. 23h59.", lue: false, dateEnvoi: "2026-07-12T08:00:00" },
  { id: 3, type: "CORRECTION_VALIDEE", message: "La correction IA de votre réponse à \"Génie logiciel\" a été validée par l'enseignant.",              lue: false, dateEnvoi: "2026-07-11T14:30:00" },
  { id: 4, type: "RESULTAT_PUBLIE",    message: "Les résultats de \"Réseaux — questions ouvertes\" ont été publiés. Votre note : 16 / 20.",             lue: true,  dateEnvoi: "2026-07-05T16:00:00" },
  { id: 5, type: "EVALUATION_OUVERTE", message: "L'évaluation \"Bases de données — quiz\" est maintenant disponible jusqu'au 10 juil. 18h00.",         lue: true,  dateEnvoi: "2026-07-08T08:00:00" },
  { id: 6, type: "RESULTAT_PUBLIE",    message: "Les résultats de \"Systèmes d'exploitation — QCM\" ont été publiés. Votre note : 15 / 20.",           lue: true,  dateEnvoi: "2026-06-20T18:00:00" },
]

const MOCK_ENSEIGNANT: NotificationItem[] = [
  { id: 1, type: "EVALUATION_OUVERTE", message: "Votre évaluation \"Structures de données — QCM\" est ouverte aux étudiants jusqu'au 12 juil. 23h59.", lue: false, dateEnvoi: "2026-07-10T08:00:00" },
  { id: 2, type: "CORRECTION_VALIDEE", message: "Toutes les corrections IA de \"Réseaux — questions ouvertes\" ont été validées.",                      lue: false, dateEnvoi: "2026-07-06T11:15:00" },
  { id: 3, type: "RESULTAT_PUBLIE",    message: "Les résultats de \"Réseaux — questions ouvertes\" ont été publiés aux 38 étudiants.",                 lue: true,  dateEnvoi: "2026-07-05T16:00:00" },
  { id: 4, type: "RESULTAT_PUBLIE",    message: "Les résultats de \"Systèmes d'exploitation — QCM\" ont été publiés aux 42 étudiants.",               lue: true,  dateEnvoi: "2026-06-20T18:00:00" },
]

function mockPour(role: Role | undefined): NotificationItem[] {
  return role === "ENSEIGNANT" ? MOCK_ENSEIGNANT : MOCK_ETUDIANT
}

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

export default function NotificationsPage() {
  const { user } = useAuth()
  const [notifs, setNotifs] = useState<NotificationItem[]>(() => mockPour(user?.role))
  const [tab, setTab]       = useState("toutes")

  const nonLues = notifs.filter(n => !n.lue).length

  const filtered = notifs.filter(n =>
    tab === "toutes" ? true : !n.lue
  )

  function marquerLue(id: number) {
    // TODO : await api.put(`/notifications/${id}/lire`)
    setNotifs(ns => ns.map(n => n.id === id ? { ...n, lue: true } : n))
  }

  function toutMarquerLu() {
    // TODO : await api.put("/notifications/lire-tout")
    setNotifs(ns => ns.map(n => ({ ...n, lue: true })))
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
          <Button variant="outline" className="rounded-xl" onClick={toutMarquerLu}>
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
      {filtered.length === 0 ? (
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
                className={[
                  "cursor-pointer rounded-2xl border-border/60 shadow-sm transition-colors hover:bg-muted/30",
                  !n.lue ? "border-l-4 border-l-primary" : "",
                ].join(" ")}
                onClick={() => !n.lue && marquerLue(n.id)}
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
                    <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" />
                  )}
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
