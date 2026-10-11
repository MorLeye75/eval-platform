import { Card, CardContent } from "@/components/ui/card"
import { CheckCircle2, Clock, FileText } from "lucide-react"

interface NotificationItem {
  id: number
  titre: string
  description: string
  date: string
  lue: boolean
  type: "EVALUATION" | "RESULTAT" | "RAPPEL"
}

const NOTIFICATIONS_MOCK: NotificationItem[] = [
  {
    id: 1,
    titre: "Nouvelle évaluation disponible",
    description: "Structures de données — QCM est maintenant ouverte jusqu'au 12 juillet.",
    date: "Il y a 2 heures",
    lue: false,
    type: "EVALUATION",
  },
  {
    id: 2,
    titre: "Résultats publiés",
    description: "Vos résultats pour Réseaux — questions ouvertes sont maintenant consultables.",
    date: "Hier à 16:30",
    lue: false,
    type: "RESULTAT",
  },
  {
    id: 3,
    titre: "Rappel d'échéance",
    description: "L'évaluation Algorithmique — exercice de code ouvre le 15 juillet.",
    date: "Il y a 2 jours",
    lue: true,
    type: "RAPPEL",
  },
]

export default function NotificationsPage() {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Notifications</h1>
        <p className="mt-1 text-muted-foreground">
          Historique de vos alertes, nouvelles évaluations et publications de résultats.
        </p>
      </div>

      <div className="flex flex-col gap-3">
        {NOTIFICATIONS_MOCK.map((item) => (
          <Card
            key={item.id}
            className={`rounded-2xl border-border/60 shadow-sm transition-all ${
              !item.lue ? "border-primary/30 bg-primary/[0.02]" : ""
            }`}
          >
            <CardContent className="flex items-start gap-4 p-5">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                {item.type === "RESULTAT" ? (
                  <CheckCircle2 className="size-5" />
                ) : item.type === "RAPPEL" ? (
                  <Clock className="size-5" />
                ) : (
                  <FileText className="size-5" />
                )}
              </span>

              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <h3 className="font-semibold text-sm">{item.titre}</h3>
                  <span className="text-xs text-muted-foreground whitespace-nowrap">
                    {item.date}
                  </span>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">
                  {item.description}
                </p>
              </div>

              {!item.lue && (
                <span className="size-2 rounded-full bg-primary shrink-0 self-center" />
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
