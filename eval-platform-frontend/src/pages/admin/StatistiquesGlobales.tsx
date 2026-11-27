import { useQuery } from "@tanstack/react-query"
import { Award, CheckCircle2, FileText, BarChart3 } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts"
import { StatCard } from "@/components/admin/AdminWidgets"
import { CLE_ADMIN_STATISTIQUES, pourcent, statistiquesGlobales, sur20 } from "@/lib/adminVue"
import { messageErreur } from "@/lib/passation"

// Statistiques globales (DS-18) : calculées par le serveur sur les copies notées, ramenées sur 20 (réussite ≥ 10/20).
// Sans copie notée, un indicateur vaut « — », jamais 0.

const tooltipStyle = {
  contentStyle: { backgroundColor: "var(--card)", border: "1px solid var(--border)", borderRadius: "0.75rem", fontSize: "0.8rem" },
}

export default function StatistiquesGlobales() {
  const requete = useQuery({ queryKey: CLE_ADMIN_STATISTIQUES, queryFn: statistiquesGlobales })
  const s = requete.data
  const parPromotion = (s?.promotions ?? [])
    .filter((p) => p.moyenneSur20 !== null)
    .map((p) => ({ promotion: p.nom, moyenne: p.moyenneSur20 }))

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Statistiques globales</h1>
        <p className="mt-1 text-muted-foreground">
          Indicateurs de l'ensemble de la plateforme, calculés sur les copies déjà notées et ramenés sur 20.
        </p>
      </div>

      {requete.isError && (
        <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {messageErreur(requete.error, "Impossible de charger les statistiques.")}
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="Moyenne plateforme" value={s ? sur20(s.moyenneSur20) : "—"} icon={Award}
                  hint={s ? `${s.nbCopiesNotees} copie${s.nbCopiesNotees > 1 ? "s" : ""} notée${s.nbCopiesNotees > 1 ? "s" : ""}` : undefined} />
        <StatCard label="Taux de réussite" value={s ? pourcent(s.tauxReussite) : "—"} icon={CheckCircle2} hint="≥ 10 / 20" />
        <StatCard label="Évaluations créées" value={s ? String(s.nbEvaluations) : "—"} icon={FileText} hint="toutes promotions" />
        <StatCard label="Taux de remise" value={s ? pourcent(s.tauxRemise) : "—"} icon={BarChart3}
                  hint={s ? `${s.nbCopies} copie${s.nbCopies > 1 ? "s" : ""} commencée${s.nbCopies > 1 ? "s" : ""}` : undefined} />
      </div>

      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Moyenne par promotion (sur 20)</CardTitle>
        </CardHeader>
        <CardContent>
          {s && parPromotion.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Aucune copie notée pour l'instant.</p>
          ) : (
            <div className="h-[240px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={parPromotion} barSize={32} margin={{ left: -20 }}>
                  <XAxis dataKey="promotion" stroke="var(--muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis domain={[0, 20]} stroke="var(--muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip {...tooltipStyle} />
                  <Bar dataKey="moyenne" name="Moyenne" fill="var(--chart-1)" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Statistiques par promotion</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border">
                {["Promotion", "Étudiants", "Évaluations", "Copies notées", "Moyenne", "Taux réussite"].map((h) => (
                  <th key={h} className="px-5 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(s?.promotions ?? []).map((p) => (
                <tr key={p.id} className="border-b border-border transition-colors last:border-none hover:bg-muted/40">
                  <td className="px-5 py-3 font-semibold">{p.nom} <span className="font-normal text-muted-foreground">{p.annee}</span></td>
                  <td className="px-5 py-3 text-muted-foreground">{p.effectif}</td>
                  <td className="px-5 py-3 text-muted-foreground">{p.nbEvaluations}</td>
                  <td className="px-5 py-3 text-muted-foreground">{p.nbCopiesNotees}</td>
                  <td className="px-5 py-3">{sur20(p.moyenneSur20)}</td>
                  <td className="px-5 py-3">
                    <Badge variant="outline" className={`rounded-full text-[11px] font-semibold ${
                      p.tauxReussite !== null && p.tauxReussite >= 75 ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground"}`}>
                      {pourcent(p.tauxReussite)}
                    </Badge>
                  </td>
                </tr>
              ))}
              {s && s.promotions.length === 0 && (
                <tr><td colSpan={6} className="px-5 py-8 text-center text-muted-foreground">Aucune promotion.</td></tr>
              )}
            </tbody>
          </table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
