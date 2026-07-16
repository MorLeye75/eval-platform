import { Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  ClipboardList,
  Award,
  CircleCheckBig,
  ArrowRight,
  CalendarClock,
} from "lucide-react"

// Données mockées — à remplacer par un appel API (React Query) quand le backend sera prêt
const stats = [
  { label: "Évaluations à venir", valeur: "2", icon: CalendarClock },
  { label: "Moyenne générale", valeur: "14,2 / 20", icon: Award },
  { label: "Taux de réussite", valeur: "86 %", icon: CircleCheckBig },
]

const evaluations = [
  {
    id: 1,
    titre: "Structures de données — QCM",
    detail: "Durée 45 min · ouverte jusqu'au 12 juil.",
    statut: "Disponible",
    variant: "success" as const,
  },
  {
    id: 2,
    titre: "Algorithmique — exercice de code",
    detail: "Durée 90 min · ouverte le 15 juil.",
    statut: "À venir",
    variant: "warning" as const,
  },
  {
    id: 3,
    titre: "Réseaux — questions ouvertes",
    detail: "Note : 16 / 20",
    statut: "Corrigée",
    variant: "muted" as const,
  },
]

function StatutBadge({ statut, variant }: { statut: string; variant: string }) {
  const styles: Record<string, string> = {
    success: "bg-primary/10 text-primary border-primary/20",
    warning:
      "bg-amber-500/10 text-amber-700 border-amber-500/20 dark:text-amber-400",
    muted: "bg-muted text-muted-foreground border-transparent",
  }
  return (
    <Badge
      variant="outline"
      className={`rounded-full px-3 py-1 text-xs font-semibold ${styles[variant]}`}
    >
      {statut}
    </Badge>
  )
}

export default function DashboardEtudiant() {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-7">
      {/* En-tête */}
      <div>
        <h1 className="flex items-center gap-2 text-3xl font-extrabold tracking-tight">
          Bonjour <span className="text-2xl">👋</span>
        </h1>
        <p className="mt-1 text-muted-foreground">
          Voici un aperçu rapide de vos évaluations et de vos résultats.
        </p>
      </div>

      {/* Cartes de statistiques (aperçu rapide) */}
      <div className="grid gap-4 md:grid-cols-3">
        {stats.map((stat) => (
          <Card
            key={stat.label}
            className="rounded-2xl border-border/60 shadow-sm transition-shadow hover:shadow-md"
          >
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {stat.label}
              </CardTitle>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <stat.icon className="size-4" />
              </span>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-extrabold tracking-tight">
                {stat.valeur}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Liste des évaluations récentes */}
      <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base font-bold">
            Mes évaluations récentes
          </CardTitle>
          <Button
            variant="ghost"
            size="sm"
            render={
              <Link to="/etudiant/evaluations">
                Tout voir
                <ArrowRight className="ml-1 size-4" />
              </Link>
            }
          />
        </CardHeader>
        <CardContent className="flex flex-col gap-0 p-0">
          {evaluations.map((evaluation, index) => (
            <div
              key={evaluation.id}
              className={`flex items-center justify-between gap-4 px-6 py-4 transition-colors hover:bg-muted/40 ${
                index !== evaluations.length - 1 ? "border-b border-border/60" : ""
              }`}
            >
              <div className="flex min-w-0 items-center gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-muted">
                  <ClipboardList className="size-4 text-muted-foreground" />
                </div>
                <div className="min-w-0">
                  <p className="truncate font-semibold">{evaluation.titre}</p>
                  <p className="text-sm text-muted-foreground">
                    {evaluation.detail}
                  </p>
                </div>
              </div>
              <StatutBadge
                statut={evaluation.statut}
                variant={evaluation.variant}
              />
            </div>
          ))}
        </CardContent>
      </Card>

      {/* Accès au tableau de bord de performances détaillé (écran 11) */}
      <Card className="rounded-2xl border-primary/15 bg-gradient-to-br from-primary/[0.06] to-primary/[0.02] shadow-sm">
        <CardContent className="flex flex-col items-start justify-between gap-4 py-6 sm:flex-row sm:items-center">
          <div className="flex items-center gap-4">
            <div className="flex size-12 items-center justify-center rounded-2xl bg-primary/10">
              <Award className="size-6 text-primary" />
            </div>
            <div>
              <p className="font-bold">Suivi de vos performances</p>
              <p className="text-sm text-muted-foreground">
                Évolution dans le temps, comparaison avec la promotion et rang.
              </p>
            </div>
          </div>
          <Button
            className="shadow-md"
            render={
              <Link to="/etudiant/performances">
                Voir mes performances détaillées
                <ArrowRight className="ml-1 size-4" />
              </Link>
            }
          />
        </CardContent>
      </Card>
    </div>
  )
}