import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
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
import { Award, CircleCheckBig, TrendingUp, TrendingDown, Minus, Users, BarChart3 } from "lucide-react"
import { messageErreur } from "@/lib/passation"
import { CLE_PERFORMANCES, formatDate, formatEcart, formatNote, mesPerformances } from "@/lib/resultats"
import type { Performances } from "@/types"

function Indicateurs({ p }: { p: Performances }) {
  const reussies = p.historique.filter((h) => h.noteSur20 >= 10).length
  const stats = [
    {
      label: "Moyenne générale",
      valeur: p.moyenneGenerale !== null ? `${formatNote(p.moyenneGenerale)} / 20` : "—",
      icon: Award,
      complement:
        p.ecartMoyenne !== null
          ? `${formatEcart(p.ecartMoyenne)} pt vs promotion`
          : "comparaison dès 5 copies notées par évaluation",
    },
    {
      label: "Taux de réussite",
      valeur: `${Math.round((reussies / p.nbEvaluations) * 100)} %`,
      icon: CircleCheckBig,
      complement: `${reussies} évaluation(s) sur ${p.nbEvaluations} avec la moyenne`,
    },
    {
      label: "Moyenne de la promotion",
      valeur: p.moyennePromotionGenerale !== null ? `${formatNote(p.moyennePromotionGenerale)} / 20` : "—",
      icon: Users,
      complement: "anonyme, sur les évaluations comparables",
    },
  ]

  return (
    <div className="grid gap-4 md:grid-cols-3">
      {stats.map((stat) => (
        <Card key={stat.label} className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <CardTitle className="text-sm font-medium text-muted-foreground">{stat.label}</CardTitle>
            <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <stat.icon className="size-4" />
            </span>
          </CardHeader>
          <CardContent className="flex flex-col gap-1">
            <div className="text-3xl font-extrabold tracking-tight">{stat.valeur}</div>
            <p className="text-xs text-muted-foreground">{stat.complement}</p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function Evolution({ p }: { p: Performances }) {
  // Libellé court et unique pour l'axe ; le titre complet apparaît au survol
  const donnees = p.historique.map((h, i) => ({
    libelle: `${i + 1}`,
    titre: h.titre,
    note: h.noteSur20,
    moyennePromotion: h.moyennePromotionSur20,
  }))

  return (
    <Card className="rounded-2xl border-border/60 shadow-sm">
      <CardHeader>
        <CardTitle className="text-base font-bold">Évolution de vos notes</CardTitle>
        <p className="text-sm text-muted-foreground">Comparaison avec la moyenne anonyme de la promotion (sur 20)</p>
      </CardHeader>
      <CardContent>
        <div className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={donnees} margin={{ top: 5, right: 10, left: -20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
              <XAxis dataKey="libelle" stroke="var(--muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
              <YAxis domain={[0, 20]} stroke="var(--muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
              <Tooltip
                contentStyle={{
                  backgroundColor: "var(--card)",
                  border: "1px solid var(--border)",
                  borderRadius: "0.75rem",
                  fontSize: "0.8rem",
                }}
                labelFormatter={(label) => donnees.find((d) => d.libelle === label)?.titre ?? String(label)}
                formatter={(value, name) => [`${formatNote(Number(value))} / 20`, String(name)]}
              />
              <Legend wrapperStyle={{ fontSize: "0.8rem", paddingTop: "0.5rem" }} />
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
                connectNulls
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}

function Historique({ p }: { p: Performances }) {
  const lignes = p.historique.slice().reverse() // la plus récente d'abord

  return (
    <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
      <CardHeader>
        <CardTitle className="text-base font-bold">Historique de vos évaluations</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-0 p-0">
        {lignes.map((point, index) => {
          const moyenne = point.moyennePromotionSur20
          const ecart = moyenne !== null ? point.noteSur20 - moyenne : null
          const IconeEcart = ecart === null || ecart === 0 ? Minus : ecart > 0 ? TrendingUp : TrendingDown

          return (
            <Link
              key={point.soumissionId}
              to={`/etudiant/resultats/${point.soumissionId}`}
              className={`flex items-center justify-between gap-4 px-6 py-4 transition-colors hover:bg-muted/40 ${
                index !== lignes.length - 1 ? "border-b border-border/60" : ""
              }`}
            >
              <div className="min-w-0">
                <p className="truncate font-semibold">{point.titre}</p>
                <p className="text-sm text-muted-foreground">
                  {formatDate(point.date)} ·{" "}
                  {moyenne !== null ? `moyenne promo ${formatNote(moyenne)} / 20` : "moyenne non communiquée (moins de 5 copies)"}
                </p>
              </div>

              <div className="flex shrink-0 items-center gap-3">
                {ecart !== null && (
                  <span
                    className={`flex items-center gap-1 text-xs font-medium ${
                      ecart > 0 ? "text-primary" : ecart < 0 ? "text-destructive" : "text-muted-foreground"
                    }`}
                  >
                    <IconeEcart className="size-3.5" />
                    {formatEcart(Math.round(ecart * 100) / 100)}
                  </span>
                )}
                <Badge
                  variant="outline"
                  className={`rounded-full px-3 py-1 text-xs font-semibold ${
                    point.noteSur20 >= 10
                      ? "border-primary/20 bg-primary/10 text-primary"
                      : "border-destructive/20 bg-destructive/10 text-destructive"
                  }`}
                >
                  {formatNote(point.noteSur20)} / 20
                </Badge>
              </div>
            </Link>
          )
        })}
      </CardContent>
    </Card>
  )
}

export default function TableauBordPerformance() {
  const { data: p, isPending, error } = useQuery({ queryKey: CLE_PERFORMANCES, queryFn: mesPerformances })

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-7">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Mes performances</h1>
        <p className="mt-1 text-muted-foreground">Suivez votre progression et comparez-vous à votre promotion.</p>
      </div>

      {isPending ? (
        <p className="py-10 text-center text-sm text-muted-foreground">Chargement…</p>
      ) : error || !p ? (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            {messageErreur(error, "Impossible de charger vos performances.")}
          </CardContent>
        </Card>
      ) : p.nbEvaluations === 0 ? (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <BarChart3 className="size-8 text-muted-foreground" />
            <p className="font-semibold">Pas encore de résultats publiés</p>
            <p className="max-w-sm text-sm text-muted-foreground">
              Vos notes apparaîtront ici dès que vos enseignants auront publié les résultats d'une évaluation.
            </p>
          </CardContent>
        </Card>
      ) : (
        <>
          <Indicateurs p={p} />
          <Evolution p={p} />
          <Historique p={p} />
        </>
      )}
    </div>
  )
}
