import { Link, useNavigate, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Award, TrendingUp, TrendingDown, BarChart3, CheckCircle2 } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts"
import { CLE_EVALUATIONS_ENSEIGNANT, mesEvaluationsEnseignant } from "@/lib/evaluationsEnseignant"
import { cleStatistiques, formaterSur20, questionsDifficiles, statistiquesEvaluation } from "@/lib/statistiquesEnseignant"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Statistiques d'une évaluation (DS-09), calculées par le serveur sur les copies notées et ramenées sur 20.
// Sans identifiant dans l'adresse (lien du menu) : la première évaluation terminée, sinon en cours.
// ============================================================

const tooltipStyle = {
  contentStyle: {
    backgroundColor: "var(--card)",
    border: "1px solid var(--border)",
    borderRadius: "0.75rem",
    fontSize: "0.8rem",
  },
}

export default function StatistiquesEvaluation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const evaluations = useQuery({ queryKey: CLE_EVALUATIONS_ENSEIGNANT, queryFn: mesEvaluationsEnseignant })
  const avecResultats = (evaluations.data ?? []).filter((e) => e.statut === "TERMINEE" || e.statut === "EN_COURS")
  const choisie = id ?? String((avecResultats.find((e) => e.statut === "TERMINEE") ?? avecResultats[0])?.id ?? "")

  const stats = useQuery({
    queryKey: cleStatistiques(choisie),
    queryFn: () => statistiquesEvaluation(choisie),
    enabled: choisie !== "",
  })

  if (!id && evaluations.isSuccess && choisie === "") {
    return <Message texte="Aucune évaluation n'a encore de résultats." />
  }
  if (stats.isError) {
    return <Message texte={messageErreur(stats.error, "Impossible de charger les statistiques.")} />
  }
  if (!stats.data) {
    return <p className="text-sm text-muted-foreground">Chargement…</p>
  }

  const s = stats.data
  const difficiles = questionsDifficiles(s.questions)
  const distribution = s.distribution.map((t) => ({ tranche: `${t.de}-${t.a}`, nb: t.nb }))
  const parQuestion = s.questions.map((q) => ({ question: `Q${q.ordre}`, taux: q.tauxReussite ?? 0 }))

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">

      {/* En-tête */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Statistiques</h1>
          <p className="mt-1 text-muted-foreground">{s.titre}</p>
        </div>
        <div className="flex items-center gap-2">
          <label htmlFor="stats-evaluation" className="text-sm font-medium">Évaluation :</label>
          <select
            id="stats-evaluation"
            value={choisie}
            onChange={(e) => navigate(`/enseignant/statistiques/${e.target.value}`)}
            className="h-9 rounded-lg border border-input bg-background px-3 text-sm"
          >
            {avecResultats.map((e) => <option key={e.id} value={e.id}>{e.titre}</option>)}
          </select>
        </div>
      </div>

      {s.nbEnCorrection > 0 && (
        <p role="status" className="rounded-xl bg-yellow-500/10 px-4 py-2.5 text-sm text-yellow-700 dark:text-yellow-400">
          {s.nbEnCorrection} copie{s.nbEnCorrection > 1 ? "s ne sont" : " n'est"} pas encore notée{s.nbEnCorrection > 1 ? "s" : ""} :
          ces statistiques évolueront à mesure des corrections.
        </p>
      )}
      {s.nbNotees === 0 && (
        <p role="status" className="rounded-xl bg-muted px-4 py-2.5 text-sm text-muted-foreground">
          Aucune copie n'est encore notée : les indicateurs apparaîtront avec les premières notes.
        </p>
      )}

      {/* Stats clés */}
      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="Moyenne" value={formaterSur20(s.moyenneSur20)} icon={Award}
          hint={s.ecartTypeSur20 === null ? undefined : `Écart-type : ${s.ecartTypeSur20.toLocaleString("fr-FR")}`} />
        <StatCard label="Taux de réussite" value={s.tauxReussite === null ? "—" : `${s.tauxReussite.toLocaleString("fr-FR")} %`}
          icon={CheckCircle2} hint="≥ 10 / 20" />
        <StatCard label="Meilleure note" value={formaterSur20(s.maxSur20)} icon={TrendingUp} dir="up" />
        <StatCard label="Note la plus basse" value={formaterSur20(s.minSur20)} icon={TrendingDown} dir="down" />
      </div>

      {/* Distribution + effectifs */}
      <div className="grid gap-4 md:grid-cols-[1.5fr_1fr]">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Distribution des notes</CardTitle>
            <p className="text-sm text-muted-foreground">{s.nbNotees} copie{s.nbNotees > 1 ? "s" : ""} notée{s.nbNotees > 1 ? "s" : ""}, sur 20</p>
          </CardHeader>
          <CardContent>
            <div className="h-[240px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={distribution} barSize={28} margin={{ left: -20 }}>
                  <XAxis dataKey="tranche" stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                  <YAxis allowDecimals={false} stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                  <Tooltip {...tooltipStyle} />
                  <Bar dataKey="nb" name="Étudiants" fill="var(--chart-1)" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Participation</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="flex flex-col gap-2 text-sm">
              <Ligne terme="Étudiants attendus" valeur={s.nbAttendus} />
              <Ligne terme="Copies remises ou commencées" valeur={s.nbCopies} />
              <Ligne terme="Copies notées" valeur={s.nbNotees} />
              <Ligne terme="En attente de correction" valeur={s.nbEnCorrection} />
              <Ligne terme="Médiane" valeur={formaterSur20(s.medianeSur20)} />
            </dl>
          </CardContent>
        </Card>
      </div>

      {/* Taux de réussite par question */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Taux de réussite par question</CardTitle>
          <p className="text-sm text-muted-foreground">Points obtenus sur points possibles, en % — en dessous de 60 % : à reprendre en cours</p>
        </CardHeader>
        <CardContent>
          <div className="h-[200px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={parQuestion} barSize={32} margin={{ left: -20 }}>
                <XAxis dataKey="question" stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} />
                <YAxis domain={[0, 100]} stroke="var(--muted-foreground)" fontSize={11} tickLine={false} axisLine={false} tickFormatter={(v) => `${v}%`} />
                <Tooltip {...tooltipStyle} formatter={(v) => [`${v}%`, "Taux de réussite"]} />
                <Bar dataKey="taux" name="Taux réussite" radius={[6, 6, 0, 0]} fill="var(--chart-1)" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Questions les plus difficiles */}
      {difficiles.length > 0 && (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Questions les plus difficiles</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 p-0 px-6 pb-4">
            {difficiles.map((q, i, arr) => (
              <div key={q.questionId} className={`flex items-center gap-4 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}>
                <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-destructive/10 text-destructive">
                  <BarChart3 className="size-[18px]" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-[13px] font-semibold">Q{q.ordre} · {q.enonce}</span>
                    <span className="text-[13px] font-bold text-destructive">{q.tauxReussite}%</span>
                  </div>
                  <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-destructive/70" style={{ width: `${q.tauxReussite}%` }} />
                  </div>
                </div>
                <Badge variant="outline" className="shrink-0 rounded-full text-[11px] bg-muted text-muted-foreground">{q.points} pts</Badge>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  )
}

// ── Sous-composants ───────────────────────────────────────────

function Message({ texte }: { texte: string }) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col items-center gap-4 py-10 text-center">
      <Card className="w-full rounded-2xl border-border/60 shadow-sm">
        <CardContent className="py-10 text-sm text-muted-foreground" role="alert">{texte}</CardContent>
      </Card>
      <Button variant="outline" render={<Link to="/enseignant/evaluations">Retour aux évaluations</Link>} />
    </div>
  )
}

function Ligne({ terme, valeur }: { terme: string; valeur: string | number }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-border/60 pb-2 last:border-0 last:pb-0">
      <dt className="text-muted-foreground">{terme}</dt>
      <dd className="font-semibold">{valeur}</dd>
    </div>
  )
}

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
