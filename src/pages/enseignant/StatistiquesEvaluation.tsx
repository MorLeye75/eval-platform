import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Award, TrendingUp, TrendingDown, BarChart3, CheckCircle2 } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  LineChart, Line, CartesianGrid,
} from "recharts"
import type { StatistiquesEvaluation as StatsType } from "@/types"

// ============================================================
// Données mockées — à remplacer par :
// const { id } = useParams()
// const { data } = useQuery({
//   queryKey: ["stats-evaluation", id],
//   queryFn: () => api.get(`/enseignant/evaluations/${id}/statistiques`)
// })
// ============================================================

const EVAL_INFO = {
  titre: "Structures de données — QCM",
  nbParticipants: 42,
  dateCorrection: "2026-07-13",
}

const mockStats: StatsType = {
  moyenne: 13.4,
  noteMax: 19.0,
  noteMin: 4.0,
  ecartType: 3.2,
  tauxReussite: 78,
}

// Distribution des notes (histogramme)
const DISTRIBUTION = [
  { tranche: "0-4",   nb: 2  },
  { tranche: "4-8",   nb: 4  },
  { tranche: "8-10",  nb: 5  },
  { tranche: "10-12", nb: 8  },
  { tranche: "12-14", nb: 10 },
  { tranche: "14-16", nb: 7  },
  { tranche: "16-18", nb: 4  },
  { tranche: "18-20", nb: 2  },
]

// Taux de réussite par question
const PAR_QUESTION = [
  { question: "Q1",  taux: 92, points: 2 },
  { question: "Q2",  taux: 74, points: 3 },
  { question: "Q3",  taux: 88, points: 2 },
  { question: "Q4",  taux: 45, points: 3 },
  { question: "Q5",  taux: 61, points: 2 },
  { question: "Q6",  taux: 38, points: 4 },
  { question: "Q7",  taux: 79, points: 2 },
  { question: "Q8",  taux: 55, points: 2 },
]

// Comparaison avec promotions précédentes
const HISTORIQUE = [
  { session: "Jan 2026", moyenne: 11.8 },
  { session: "Mar 2026", moyenne: 12.4 },
  { session: "Mai 2026", moyenne: 13.1 },
  { session: "Juil 2026", moyenne: 13.4 },
]

const tooltipStyle = {
  contentStyle: {
    backgroundColor: "var(--card)",
    border: "1px solid var(--border)",
    borderRadius: "0.75rem",
    fontSize: "0.8rem",
  },
}

// Top 5 difficultés (questions les plus ratées)
const DIFFICULTES = PAR_QUESTION
  .filter(q => q.taux < 60)
  .sort((a, b) => a.taux - b.taux)
  .slice(0, 5)

export default function StatistiquesEvaluation() {
  const s = mockStats

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">

      {/* En-tête */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Statistiques</h1>
        <p className="mt-1 text-muted-foreground">{EVAL_INFO.titre}</p>
      </div>

      {/* Stats clés */}
      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="Moyenne"        value={`${s.moyenne} / 20`}   icon={Award}        dir="up"   hint={`Écart-type : ${s.ecartType}`} />
        <StatCard label="Taux de réussite" value={`${s.tauxReussite} %`} icon={CheckCircle2} dir="up"   hint="≥ 10 / 20" />
        <StatCard label="Meilleure note" value={`${s.noteMax} / 20`}   icon={TrendingUp}   dir="up"                />
        <StatCard label="Note la plus basse" value={`${s.noteMin} / 20`} icon={TrendingDown} dir="down"            />
      </div>

      {/* Distribution + Historique */}
      <div className="grid gap-4 md:grid-cols-[1.5fr_1fr]">

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Distribution des notes</CardTitle>
            <p className="text-sm text-muted-foreground">{EVAL_INFO.nbParticipants} étudiants</p>
          </CardHeader>
          <CardContent>
            <div className="h-[240px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={DISTRIBUTION} barSize={28} margin={{ left: -20 }}>
                  <XAxis dataKey="tranche" stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                  <YAxis stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                  <Tooltip {...tooltipStyle} />
                  <Bar dataKey="nb" name="Étudiants" fill="var(--chart-1)" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Évolution de la moyenne</CardTitle>
            <p className="text-sm text-muted-foreground">Sessions précédentes</p>
          </CardHeader>
          <CardContent>
            <div className="h-[240px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={HISTORIQUE} margin={{ left: -20, top: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                  <XAxis dataKey="session" stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                  <YAxis domain={[0, 20]} stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                  <Tooltip {...tooltipStyle} />
                  <Line type="monotone" dataKey="moyenne" name="Moyenne" stroke="var(--chart-1)" strokeWidth={2.5} dot={{ r: 4 }} activeDot={{ r: 6 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Taux de réussite par question */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Taux de réussite par question</CardTitle>
          <p className="text-sm text-muted-foreground">
            Questions en rouge : taux &lt; 60% — à reprendre en cours
          </p>
        </CardHeader>
        <CardContent>
          <div className="h-[200px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={PAR_QUESTION} barSize={32} margin={{ left: -20 }}>
                <XAxis dataKey="question" stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                <YAxis domain={[0, 100]} stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} tickFormatter={(v) => `${v}%`} />
                <Tooltip
                  {...tooltipStyle}
                  formatter={(v) => [`${v}%`, "Taux de réussite"]}
                />
                <Bar
                  dataKey="taux"
                  name="Taux réussite"
                  radius={[6, 6, 0, 0]}
                  fill="var(--chart-1)"
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Questions les plus difficiles */}
      {DIFFICULTES.length > 0 && (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Questions les plus difficiles</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 p-0 px-6 pb-4">
            {DIFFICULTES.map((q, i, arr) => (
              <div
                key={q.question}
                className={`flex items-center gap-4 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-destructive/10 text-destructive">
                  <BarChart3 className="size-[18px]" />
                </span>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <span className="text-[13px] font-semibold">{q.question}</span>
                    <span className="text-[13px] font-bold text-destructive">{q.taux}%</span>
                  </div>
                  <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-destructive/70"
                      style={{ width: `${q.taux}%` }}
                    />
                  </div>
                </div>
                <Badge variant="outline" className="shrink-0 rounded-full text-[11px] bg-muted text-muted-foreground">
                  {q.points} pts
                </Badge>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  )
}

// ── Sous-composant ────────────────────────────────────────────

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
        <div className="text-2xl font-extrabold tracking-tight">{value}</div>
        {hint && <p className={`mt-0.5 text-[11px] ${hintCls}`}>{hint}</p>}
      </CardContent>
    </Card>
  )
}
