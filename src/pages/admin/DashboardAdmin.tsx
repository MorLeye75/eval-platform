<<<<<<< HEAD
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Users, FileText, CheckCircle2, AlertTriangle, Database, Shield, Award } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts"
import type { StatistiquesGlobales } from "@/types"

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
=======
import { Users, FileText, CheckCircle2, AlertTriangle, Database, Shield, Award } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
} from "recharts"

/* ---------------------------------------------------------------
   Données mockées — à remplacer par :
   const { data } = useQuery({ queryKey: ["admin-dashboard"], queryFn: ... })
--------------------------------------------------------------- */

const roleData = [
  { name: "Étudiants", value: 298 },
  { name: "Enseignants", value: 38 },
  { name: "Admins", value: 6 },
]
const PIE_COLORS = ["hsl(var(--primary))", "hsl(var(--primary) / 0.55)", "hsl(var(--primary) / 0.25)"]
>>>>>>> 24536201f53c0a90f49988b2761649297d4f4ea7

export default function DashboardAdmin() {
  const s = mockStats

  return (
<<<<<<< HEAD
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

type Tone = "green" | "amber" | "red" | "blue" | "gray"

// Classes purement sémantiques — aucun hex
const TONE_CLS: Record<Tone, string> = {
  green: "bg-primary/10 text-primary",
  amber: "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400",
  red:   "bg-destructive/10 text-destructive",
  blue:  "bg-blue-500/10 text-blue-700 dark:text-blue-400",
  gray:  "bg-muted text-muted-foreground",
}

function ToneBadge({ tone, dot, children }: { tone: Tone; dot?: boolean; children: React.ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-[3px] text-[11px] font-semibold ${TONE_CLS[tone]}`}>
      {dot && <span className="size-1.5 rounded-full bg-current" />}
      {children}
    </span>
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
        <div className="text-3xl font-extrabold tracking-tight">{value}</div>
        {hint && <p className={`mt-0.5 text-[11px] ${hintCls}`}>{hint}</p>}
      </CardContent>
    </Card>
  )
}

function ActivityRow({ icon: Icon, title, sub, tone, tag }: {
=======
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Tableau de bord</h1>
        <p className="text-sm text-muted-foreground">
          Vue d'ensemble de l'activité de la plateforme.
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Utilisateurs" value="342" icon={Users} hint="+18 ce mois" dir="up" />
        <StatCard label="Évaluations actives" value="7" icon={FileText} hint="3 en cours" />
        <StatCard label="Soumissions (7j)" value="1 284" icon={CheckCircle2} hint="+9% vs semaine" dir="up" />
        <StatCard label="Incidents signalés" value="2" icon={AlertTriangle} hint="à examiner" dir="down" />
      </div>

      {/* Activité + donut */}
      <div className="grid gap-4 md:grid-cols-[1.6fr_1fr]">
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="mb-3 flex items-center justify-between">
            <span className="font-semibold">Activité récente</span>
            <span className="cursor-pointer text-xs text-primary">Tout voir →</span>
          </div>
          <ActivityRow icon={Users} title="Nouveau compte enseignant" sub="Dr. Fatou Sarr · il y a 12 min" tone="green" tag="Créé" />
          <ActivityRow icon={FileText} title="Évaluation publiée" sub="Algorithmique — exercice de code · 1 h" tone="blue" tag="Publiée" />
          <ActivityRow icon={AlertTriangle} title="Changements d'onglet détectés" sub="Étudiant #217 · Réseaux QCM · 2 h" tone="amber" tag="Alerte" />
          <ActivityRow icon={CheckCircle2} title="Résultats publiés" sub="Bases de données — quiz · 3 h" tone="green" tag="Publiés" />
          <ActivityRow icon={Users} title="Compte désactivé" sub="Étudiant inactif · 5 h" tone="gray" tag="Désactivé" />
        </div>

        <div className="rounded-xl border border-border bg-card p-5">
          <span className="font-semibold">Répartition des rôles</span>
          <div className="mt-3 h-[160px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={roleData}
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={72}
                  dataKey="value"
                  paddingAngle={2}
                >
                  {roleData.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ fontFamily: "inherit", fontSize: 12, borderRadius: 8 }}
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
        </div>
      </div>

      {/* État des services */}
      <div className="rounded-xl border border-border bg-card p-5">
        <span className="font-semibold">État des services</span>
        <div className="mt-3 grid gap-3 md:grid-cols-3">
          <ServiceCard icon={Database} name="Base de données" status="Opérationnel" tone="green" />
          <ServiceCard icon={Shield} name="Judge0 (exécution code)" status="Opérationnel" tone="green" />
          <ServiceCard icon={Award} name="API OpenAI" status="Latence élevée" tone="amber" />
        </div>
      </div>
    </div>
  )
}

/* ── Sous-composants ────────────────────────────────────────── */

function StatCard({
  label, value, icon: Icon, hint, dir,
}: {
  label: string; value: string; icon: LucideIcon; hint?: string; dir?: "up" | "down"
}) {
  const hintClass =
    dir === "up" ? "text-primary" : dir === "down" ? "text-destructive" : "text-muted-foreground"
  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm text-muted-foreground">{label}</span>
        <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <Icon className="size-4" />
        </span>
      </div>
      <div className="mt-3 text-3xl font-extrabold tracking-tight">{value}</div>
      {hint && <div className={`mt-0.5 text-[11px] ${hintClass}`}>{hint}</div>}
    </div>
  )
}

type Tone = "green" | "amber" | "red" | "blue" | "gray"
const TONE_CLASS: Record<Tone, string> = {
  green: "bg-primary/10 text-primary",
  amber: "bg-yellow-100 text-yellow-700",
  red: "bg-destructive/10 text-destructive",
  blue: "bg-blue-100 text-blue-700",
  gray: "bg-muted text-muted-foreground",
}

function ActivityRow({
  icon: Icon, title, sub, tone, tag,
}: {
>>>>>>> 24536201f53c0a90f49988b2761649297d4f4ea7
  icon: LucideIcon; title: string; sub: string; tone: Tone; tag: string
}) {
  return (
    <div className="flex items-center gap-3 border-b border-border py-3 last:border-none">
<<<<<<< HEAD
      <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
        <Icon className="size-[18px]" />
      </span>
=======
      <div className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
        <Icon className="size-[18px]" />
      </div>
>>>>>>> 24536201f53c0a90f49988b2761649297d4f4ea7
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-semibold">{title}</div>
        <div className="mt-0.5 text-[11px] text-muted-foreground">{sub}</div>
      </div>
<<<<<<< HEAD
      <ToneBadge tone={tone}>{tag}</ToneBadge>
    </div>
  )
=======
      <span className={`inline-flex items-center rounded-full px-2.5 py-[3px] text-[11px] font-semibold ${TONE_CLASS[tone]}`}>
        {tag}
      </span>
    </div>
  )
}

function ServiceCard({
  icon: Icon, name, status, tone,
}: {
  icon: LucideIcon; name: string; status: string; tone: Tone
}) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border p-3.5">
      <div className="flex size-9 items-center justify-center rounded-[10px] bg-muted text-primary">
        <Icon className="size-[18px]" />
      </div>
      <div>
        <div className="text-[13px] font-semibold">{name}</div>
        <span className={`mt-1 inline-flex items-center gap-1.5 rounded-full px-2.5 py-[3px] text-[11px] font-semibold ${TONE_CLASS[tone]}`}>
          <span className="size-1.5 rounded-full bg-current" />
          {status}
        </span>
      </div>
    </div>
  )
>>>>>>> 24536201f53c0a90f49988b2761649297d4f4ea7
}
