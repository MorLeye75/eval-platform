import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Users, FileText, CheckCircle2, AlertTriangle, Database, Shield, Award } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts"
import type { StatistiquesGlobales } from "@/types"
import { StatCard, ToneBadge, type Tone } from "@/components/admin/AdminWidgets"

/* ---------------------------------------------------------------
   Données mockées — à remplacer par :
   const { data } = useQuery({ queryKey: ["admin-dashboard"], queryFn: () => api.get("/admin/dashboard") })
--------------------------------------------------------------- */
const mockStats: StatistiquesGlobales = {
  moyennePlateforme: 13.4,
  tauxReussite: 78,
  nbEvaluationsCreees: 156,
  tauxCompletion: 91,
  nbUtilisateurs: 342,
  nbEvaluationsActives: 7,
  nbSoumissions7j: 1284,
  nbIncidents: 2,
}

const roleData = [
  { name: "Étudiants",   value: 298 },
  { name: "Enseignants", value: 38  },
  { name: "Admins",      value: 6   },
]

const ACTIVITE = [
  { icon: Users,         title: "Nouveau compte enseignant",       sub: "Dr. Fatou Sarr · il y a 12 min",                    tone: "green" as Tone, tag: "Créé"      },
  { icon: FileText,      title: "Évaluation publiée",              sub: "Algorithmique — exercice de code · 1 h",             tone: "blue"  as Tone, tag: "Publiée"   },
  { icon: AlertTriangle, title: "Changements d'onglet détectés",   sub: "Étudiant #217 · Réseaux QCM · 2 h",                 tone: "amber" as Tone, tag: "Alerte"    },
  { icon: CheckCircle2,  title: "Résultats publiés",               sub: "Bases de données — quiz · 3 h",                     tone: "green" as Tone, tag: "Publiés"   },
  { icon: Users,         title: "Compte désactivé",                sub: "Étudiant inactif · 5 h",                            tone: "gray"  as Tone, tag: "Désactivé" },
]

const SERVICES = [
  { icon: Database, name: "Base de données",       status: "Opérationnel",  tone: "green" as Tone },
  { icon: Shield,   name: "Judge0 (exécution code)", status: "Opérationnel", tone: "green" as Tone },
  { icon: Award,    name: "API OpenAI",             status: "Latence élevée", tone: "amber" as Tone },
]

// Couleurs Recharts : variables CSS du thème (oklch via shadcn)
const PIE_COLORS = ["var(--chart-1)", "var(--chart-2)", "var(--chart-3)"]

export default function DashboardAdmin() {
  const s = mockStats

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Tableau de bord</h1>
        <p className="mt-1 text-muted-foreground">Vue d'ensemble de l'activité de la plateforme.</p>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="Utilisateurs"        value={String(s.nbUtilisateurs)}    icon={Users}         hint="+18 ce mois"      dir="up"   />
        <StatCard label="Évaluations actives" value={String(s.nbEvaluationsActives)} icon={FileText}   hint="3 en cours"                />
        <StatCard label="Soumissions (7j)"    value={s.nbSoumissions7j.toLocaleString("fr-FR")} icon={CheckCircle2} hint="+9% vs semaine" dir="up" />
        <StatCard label="Incidents signalés"  value={String(s.nbIncidents)}        icon={AlertTriangle} hint="à examiner"      dir="down" />
      </div>

      {/* Activité + donut */}
      <div className="grid gap-4 md:grid-cols-[1.6fr_1fr]">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-base font-bold">Activité récente</CardTitle>
            <span className="cursor-pointer text-xs text-primary">Tout voir →</span>
          </CardHeader>
          <CardContent className="p-0 px-6 pb-4">
            {ACTIVITE.map((a, i) => (
              <ActivityRow key={i} icon={a.icon} title={a.title} sub={a.sub} tone={a.tone} tag={a.tag} />
            ))}
          </CardContent>
        </Card>

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Répartition des rôles</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="h-[160px]">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={roleData} cx="50%" cy="50%" innerRadius={48} outerRadius={68} dataKey="value" paddingAngle={2}>
                    {roleData.map((_, i) => (
                      <Cell key={i} fill={PIE_COLORS[i]} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "var(--card)",
                      border: "1px solid var(--border)",
                      borderRadius: "0.75rem",
                      fontSize: "0.8rem",
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-2 divide-y divide-border">
              {roleData.map((r) => (
                <div key={r.name} className="flex justify-between py-2 text-sm">
                  <span className="text-muted-foreground">{r.name}</span>
                  <span className="font-semibold">{r.value}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Services */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">État des services</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 md:grid-cols-3">
            {SERVICES.map((svc) => (
              <div key={svc.name} className="flex items-center gap-3 rounded-xl border border-border p-3.5">
                <span className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <svc.icon className="size-[18px]" />
                </span>
                <div>
                  <div className="text-[13px] font-semibold">{svc.name}</div>
                  <ToneBadge tone={svc.tone} dot>{svc.status}</ToneBadge>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

/* ── Sous-composants ───────────────────────────────────────── */

function ActivityRow({ icon: Icon, title, sub, tone, tag }: {
  icon: LucideIcon; title: string; sub: string; tone: Tone; tag: string
}) {
  return (
    <div className="flex items-center gap-3 border-b border-border py-3 last:border-none">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
        <Icon className="size-[18px]" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-semibold">{title}</div>
        <div className="mt-0.5 text-[11px] text-muted-foreground">{sub}</div>
      </div>
      <ToneBadge tone={tone}>{tag}</ToneBadge>
    </div>
  )
}
