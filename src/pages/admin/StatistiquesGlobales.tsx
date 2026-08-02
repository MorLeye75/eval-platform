import { Award, CheckCircle2, FileText, BarChart3 } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend,
} from "recharts"
import type { StatistiquesGlobales as StatsType, StatistiquesEvaluation } from "@/types"

/* ---------------------------------------------------------------
   Données mockées — à remplacer par :
   const { data } = useQuery({ queryKey: ["admin-stats"], queryFn: () => api.get("/admin/statistiques") })

   StatsByPromotion : GET /admin/statistiques/promotions
   (remplace les départements — Promotion existe dans le diagramme de classes)
--------------------------------------------------------------- */

const mockStats: StatsType = {
  moyennePlateforme: 13.4,
  tauxReussite: 78,
  nbEvaluationsCreees: 156,
  tauxCompletion: 91,
  nbUtilisateurs: 342,
  nbEvaluationsActives: 7,
  nbSoumissions7j: 1284,
  nbIncidents: 2,
}

const SUB_DATA = [
  { mois: "Jan", value: 145 },
  { mois: "Fév", value: 198 },
  { mois: "Mar", value: 176 },
  { mois: "Avr", value: 234 },
  { mois: "Mai", value: 289 },
  { mois: "Juin", value: 312 },
  { mois: "Juil", value: 284 },
]

const TYPE_DATA = [
  { name: "QCM",      value: 42 },
  { name: "Code",     value: 23 },
  { name: "Ouvertes", value: 20 },
  { name: "Quiz",     value: 15 },
]

// Stats par promotion — alignées sur la classe Promotion du diagramme
// { nom, annee, effectif } + stats issues de Statistiques
interface StatPromotion {
  id: number
  nom: string
  annee: string
  effectif: number
  stats: Pick<StatistiquesEvaluation, "moyenne" | "tauxReussite">
  nbEvaluations: number
}

const STATS_PROMOTIONS: StatPromotion[] = [
  { id: 1, nom: "DIC1",  annee: "2025-2026", effectif: 45, stats: { moyenne: 14.1, tauxReussite: 82 }, nbEvaluations: 24 },
  { id: 2, nom: "DIC2",  annee: "2025-2026", effectif: 38, stats: { moyenne: 13.2, tauxReussite: 76 }, nbEvaluations: 31 },
  { id: 3, nom: "DIC3",  annee: "2025-2026", effectif: 32, stats: { moyenne: 12.8, tauxReussite: 71 }, nbEvaluations: 28 },
  { id: 4, nom: "DGEI1", annee: "2025-2026", effectif: 41, stats: { moyenne: 13.5, tauxReussite: 79 }, nbEvaluations: 19 },
]

const PIE_COLORS = ["var(--chart-1)", "var(--chart-2)", "var(--chart-3)", "var(--chart-4)"]

const tooltipStyle = {
  contentStyle: {
    backgroundColor: "var(--card)",
    border: "1px solid var(--border)",
    borderRadius: "0.75rem",
    fontSize: "0.8rem",
  },
}

export default function StatistiquesGlobales() {
  const s = mockStats

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Statistiques globales</h1>
        <p className="mt-1 text-muted-foreground">
          Indicateurs de performance de l'ensemble de la plateforme.
        </p>
      </div>

      {/* Stats globales */}
      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="Moyenne plateforme" value={`${s.moyennePlateforme} / 20`} icon={Award}        hint="+0,3 vs semestre" dir="up" />
        <StatCard label="Taux de réussite"   value={`${s.tauxReussite} %`}          icon={CheckCircle2} hint="≥ 10/20"          dir="up" />
        <StatCard label="Évaluations créées" value={String(s.nbEvaluationsCreees)}  icon={FileText}     hint="cette année"             />
        <StatCard label="Taux de complétion" value={`${s.tauxCompletion} %`}         icon={BarChart3}    hint="soumissions finalisées" dir="up" />
      </div>

      {/* Graphiques */}
      <div className="grid gap-4 md:grid-cols-[1.6fr_1fr]">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Soumissions par mois</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[260px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={SUB_DATA} barSize={28} margin={{ left: -20 }}>
                  <XAxis dataKey="mois" stroke="var(--muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="var(--muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip {...tooltipStyle} />
                  <Bar dataKey="value" name="Soumissions" fill="var(--chart-1)" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Répartition par type</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[260px]">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={TYPE_DATA} cx="50%" cy="45%" innerRadius={55} outerRadius={80} dataKey="value" paddingAngle={2}>
                    {TYPE_DATA.map((_, i) => (
                      <Cell key={i} fill={PIE_COLORS[i]} />
                    ))}
                  </Pie>
                  <Tooltip {...tooltipStyle} />
                  <Legend iconType="circle" iconSize={10} wrapperStyle={{ fontSize: "0.75rem", paddingTop: "0.5rem" }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Stats par promotion — remplace les départements */}
      <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Statistiques par promotion</CardTitle>
          <p className="text-sm text-muted-foreground">Année {STATS_PROMOTIONS[0]?.annee}</p>
        </CardHeader>
        <CardContent className="p-0">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border">
                {["Promotion", "Étudiants", "Évaluations", "Moyenne", "Taux réussite"].map((h) => (
                  <th key={h} className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {STATS_PROMOTIONS.map((p) => (
                <tr key={p.id} className="border-b border-border last:border-none transition-colors hover:bg-muted/40">
                  <td className="px-5 py-3 font-semibold">{p.nom}</td>
                  <td className="px-5 py-3 text-muted-foreground">{p.effectif}</td>
                  <td className="px-5 py-3 text-muted-foreground">{p.nbEvaluations}</td>
                  <td className="px-5 py-3">{p.stats.moyenne.toFixed(1)} / 20</td>
                  <td className="px-5 py-3">
                    <Badge
                      variant="outline"
                      className={`rounded-full text-[11px] font-semibold ${
                        p.stats.tauxReussite >= 75
                          ? "bg-primary/10 text-primary"
                          : "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400"
                      }`}
                    >
                      {p.stats.tauxReussite} %
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  )
}

/* ── Sous-composant ──────────────────────────────────────────── */

function StatCard({ label, value, icon: Icon, hint, dir }: {
  label: string; value: string; icon: LucideIcon; hint?: string; dir?: "up" | "down"
}) {
  const hintCls = dir === "up" ? "text-primary" : dir === "down" ? "text-destructive" : "text-muted-foreground"
  return (
    <Card className="rounded-2xl border-border/60 shadow-sm">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <Icon className="size-4" />
        </span>
      </CardHeader>
      <CardContent>
        <div className="text-3xl font-extrabold tracking-tight">{value}</div>
        {hint && <p className={`mt-0.5 text-[11px] ${hintCls}`}>{hint}</p>}
      </CardContent>
    </Card>
  )
}
