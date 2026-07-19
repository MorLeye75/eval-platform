import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { performanceMock } from "@/mocks/evaluations"
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts"
import {
  Award,
  CircleCheckBig,
  Trophy,
  TrendingUp,
  TrendingDown,
  Minus,
} from "lucide-react"

function formatNote(note: number) {
  return note.toLocaleString("fr-FR", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })
}

export default function TableauBordPerformance() {
  const p = performanceMock
  const ecartPromotion = p.moyenneGenerale - p.moyennePromotionGenerale

  const stats = [
    {
      label: "Moyenne générale",
      valeur: `${formatNote(p.moyenneGenerale)} / 20`,
      icon: Award,
      complement:
        ecartPromotion >= 0
          ? `+${formatNote(ecartPromotion)} pt vs promotion`
          : `${formatNote(ecartPromotion)} pt vs promotion`,
    },
    {
      label: "Taux de réussite",
      valeur: `${p.tauxReussite} %`,
      icon: CircleCheckBig,
      complement: "évaluations avec la moyenne",
    },
    {
      label: "Rang",
      valeur: `${p.rang}ᵉ`,
      icon: Trophy,
      complement: `sur ${p.effectifPromotion} étudiants`,
    },
  ]

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-7">
      {/* En-tête */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">
          Mes performances
        </h1>
        <p className="mt-1 text-muted-foreground">
          Suivez votre progression et comparez-vous à votre promotion.
        </p>
      </div>

      {/* Indicateurs clés */}
      <div className="grid gap-4 md:grid-cols-3">
        {stats.map((stat) => (
          <Card
            key={stat.label}
            className="rounded-2xl border-border/60 shadow-sm"
          >
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {stat.label}
              </CardTitle>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <stat.icon className="size-4" />
              </span>
            </CardHeader>
            <CardContent className="flex flex-col gap-1">
              <div className="text-3xl font-extrabold tracking-tight">
                {stat.valeur}
              </div>
              <p className="text-xs text-muted-foreground">{stat.complement}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Graphique d'évolution */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader>
          <CardTitle className="text-base font-bold">
            Évolution de vos notes
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            Comparaison avec la moyenne anonyme de la promotion
          </p>
        </CardHeader>
        <CardContent>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={p.historique}
                margin={{ top: 5, right: 10, left: -20, bottom: 5 }}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke="var(--border)"
                  vertical={false}
                />
                <XAxis
                  dataKey="evaluation"
                  stroke="var(--muted-foreground)"
                  fontSize={12}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  domain={[0, 20]}
                  stroke="var(--muted-foreground)"
                  fontSize={12}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: "var(--card)",
                    border: "1px solid var(--border)",
                    borderRadius: "0.75rem",
                    fontSize: "0.8rem",
                  }}
                  labelFormatter={(label) => {
                    const point = p.historique.find(
                      (h) => h.evaluation === label
                    )
                    return point ? point.titreComplet : String(label)
                  }}
                  formatter={(value, name) => [
                    `${formatNote(Number(value))} / 20`,
                    String(name),
                  ]}
                />
                <Legend
                  wrapperStyle={{ fontSize: "0.8rem", paddingTop: "0.5rem" }}
                />
                <Line
                  type="monotone"
                  dataKey="note"
                  name="Ma note"
                  stroke="var(--chart-1)"
                  strokeWidth={2.5}
                  dot={{ r: 4 }}
                  activeDot={{ r: 6 }}
                />
                <Line
                  type="monotone"
                  dataKey="moyennePromotion"
                  name="Moyenne promotion"
                  stroke="var(--muted-foreground)"
                  strokeWidth={2}
                  strokeDasharray="5 5"
                  dot={{ r: 3 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Historique détaillé */}
      <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
        <CardHeader>
          <CardTitle className="text-base font-bold">
            Historique de vos évaluations
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-0 p-0">
          {p.historique
            .slice()
            .reverse()
            .map((point, index, tableau) => {
              const ecart = point.note - point.moyennePromotion
              const IconeEcart =
                ecart > 0 ? TrendingUp : ecart < 0 ? TrendingDown : Minus

              return (
                <div
                  key={point.titreComplet}
                  className={`flex items-center justify-between gap-4 px-6 py-4 ${
                    index !== tableau.length - 1
                      ? "border-b border-border/60"
                      : ""
                  }`}
                >
                  <div className="min-w-0">
                    <p className="truncate font-semibold">
                      {point.titreComplet}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {point.date} · moyenne promo{" "}
                      {formatNote(point.moyennePromotion)} /{" "}
                      {point.noteStandard}
                    </p>
                  </div>

                  <div className="flex shrink-0 items-center gap-3">
                    <span
                      className={`flex items-center gap-1 text-xs font-medium ${
                        ecart > 0
                          ? "text-primary"
                          : ecart < 0
                            ? "text-destructive"
                            : "text-muted-foreground"
                      }`}
                    >
                      <IconeEcart className="size-3.5" />
                      {ecart > 0 ? "+" : ""}
                      {formatNote(ecart)}
                    </span>
                    <Badge
                      variant="outline"
                      className={`rounded-full px-3 py-1 text-xs font-semibold ${
                        point.note >= 10
                          ? "border-primary/20 bg-primary/10 text-primary"
                          : "border-destructive/20 bg-destructive/10 text-destructive"
                      }`}
                    >
                      {formatNote(point.note)} / {point.noteStandard}
                    </Badge>
                  </div>
                </div>
              )
            })}
        </CardContent>
      </Card>
    </div>
  )
}