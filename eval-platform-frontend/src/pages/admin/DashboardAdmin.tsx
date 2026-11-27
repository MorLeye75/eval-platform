import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Users, FileText, CheckCircle2, AlertTriangle, Database, Award } from "lucide-react"
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts"
import { StatCard, ToneBadge, type Tone } from "@/components/admin/AdminWidgets"
import { CLE_ADMIN_TABLEAU, tableauDeBordAdmin } from "@/lib/adminVue"
import { messageErreur } from "@/lib/passation"

// Tableau de bord de l'administrateur (DS-18) : chiffres du serveur, « — » pendant le chargement. Seuls les services dont
// l'état est réellement connu du serveur sont affichés.

const PIE_COLORS = ["var(--chart-1)", "var(--chart-2)", "var(--chart-3)"]
const ETATS: Record<string, { libelle: string; tone: Tone }> = {
  OPERATIONNEL: { libelle: "Opérationnel", tone: "green" },
  CONFIGURE: { libelle: "Configuré", tone: "blue" },
}

export default function DashboardAdmin() {
  const requete = useQuery({ queryKey: CLE_ADMIN_TABLEAU, queryFn: tableauDeBordAdmin })
  const d = requete.data
  const v = (valeur: number | undefined) => (valeur === undefined ? "—" : valeur.toLocaleString("fr-FR"))

  const roles = d ? [
    { name: "Étudiants", value: d.parRole.etudiants },
    { name: "Enseignants", value: d.parRole.enseignants },
    { name: "Administrateurs", value: d.parRole.admins },
  ] : []

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Tableau de bord</h1>
        <p className="mt-1 text-muted-foreground">Vue d'ensemble de l'activité de la plateforme.</p>
      </div>

      {requete.isError && (
        <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {messageErreur(requete.error, "Impossible de charger le tableau de bord.")}
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="Comptes actifs" value={v(d?.nbUtilisateurs)} icon={Users} hint="tous rôles" />
        <StatCard label="Évaluations en cours" value={v(d?.nbEvaluationsEnCours)} icon={FileText}
                  hint={d ? `${d.nbEvaluationsPubliees} publiée${d.nbEvaluationsPubliees > 1 ? "s" : ""} à venir` : undefined} />
        <StatCard label="Copies commencées (7 j)" value={v(d?.nbSoumissions7j)} icon={CheckCircle2} />
        <StatCard label="Alertes anti-triche (7 j)" value={v(d?.nbAlertes7j)} icon={AlertTriangle}
                  hint="à examiner" dir={d && d.nbAlertes7j > 0 ? "down" : undefined} />
      </div>

      <div className="grid gap-4 md:grid-cols-[1fr_1.2fr]">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Répartition des rôles</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[160px]">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={roles} cx="50%" cy="50%" innerRadius={48} outerRadius={68} dataKey="value" paddingAngle={2}>
                    {roles.map((_, i) => <Cell key={i} fill={PIE_COLORS[i]} />)}
                  </Pie>
                  <Tooltip contentStyle={{ backgroundColor: "var(--card)", border: "1px solid var(--border)", borderRadius: "0.75rem", fontSize: "0.8rem" }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-2 divide-y divide-border">
              {roles.map((r) => (
                <div key={r.name} className="flex justify-between py-2 text-sm">
                  <span className="text-muted-foreground">{r.name}</span>
                  <span className="font-semibold">{r.value}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">État des services</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {(d?.services ?? []).map((svc) => {
              const etat = ETATS[svc.etat] ?? { libelle: svc.etat, tone: "gray" as Tone }
              return (
                <div key={svc.nom} className="flex items-center gap-3 rounded-xl border border-border p-3.5">
                  <span className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    {svc.nom === "Base de données" ? <Database className="size-[18px]" /> : <Award className="size-[18px]" />}
                  </span>
                  <div>
                    <div className="text-[13px] font-semibold">{svc.nom}</div>
                    <div className="mt-0.5 flex items-center gap-2">
                      <ToneBadge tone={etat.tone} dot>{etat.libelle}</ToneBadge>
                      {svc.detail && <span className="text-[11px] text-muted-foreground">{svc.detail}</span>}
                    </div>
                  </div>
                </div>
              )
            })}
            {!d && <p className="text-sm text-muted-foreground">Chargement…</p>}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
