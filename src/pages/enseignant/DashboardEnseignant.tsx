import { Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/context/AuthContext"
import {
  ClipboardList,
  Plus,
  Users,
  BarChart3,
  CheckCircle2,
  Clock,
  ArrowRight,
  FileText,
  Eye,
} from "lucide-react"
import type { Evaluation, StatutEvaluation } from "@/types"

// ============================================================
// Données mockées — à remplacer par :
// const { data } = useQuery({ queryKey: ["enseignant-dashboard"], queryFn: () => api.get("/enseignant/dashboard") })
// ============================================================

const stats = [
  { label: "Évaluations actives",  valeur: "3",  icon: ClipboardList },
  { label: "Étudiants à corriger", valeur: "28", icon: Users         },
  { label: "Corrections IA en attente", valeur: "5", icon: CheckCircle2 },
  { label: "Moy. promotion (dernière éval.)", valeur: "13,4 / 20", icon: BarChart3 },
]

interface EvaluationResume extends Pick<Evaluation, "id" | "titre" | "statut"> {
  nbSoumissions: number
  nbAttendus: number
  dateFermeture: string
}

const evaluationsRecentes: EvaluationResume[] = [
  { id: 1, titre: "Structures de données — QCM",      statut: "EN_COURS",  nbSoumissions: 32, nbAttendus: 45, dateFermeture: "2026-07-12T23:59:00" },
  { id: 2, titre: "Algorithmique — exercice de code", statut: "BROUILLON", nbSoumissions: 0,  nbAttendus: 45, dateFermeture: "2026-07-15T23:59:00" },
  { id: 3, titre: "Réseaux — questions ouvertes",     statut: "TERMINEE",  nbSoumissions: 38, nbAttendus: 38, dateFermeture: "2026-07-05T23:59:00" },
  { id: 4, titre: "Bases de données — quiz",          statut: "PUBLIEE",   nbSoumissions: 40, nbAttendus: 45, dateFermeture: "2026-07-10T18:00:00" },
]

// Corrections IA en attente de validation
const correctionsEnAttente = [
  { id: 1, evaluation: "Génie logiciel — questions ouvertes", etudiant: "Moussa Diop",   question: "Différence agrégation / composition", noteIA: 4.5 },
  { id: 2, evaluation: "Génie logiciel — questions ouvertes", etudiant: "Awa Ndiaye",    question: "Avantages architecture découplée",    noteIA: 3.0 },
  { id: 3, evaluation: "Réseaux — questions ouvertes",        etudiant: "Cheikh Ba",     question: "Protocoles réseau — analyse",         noteIA: 2.5 },
]

// ──────────────────────────────────────────────────────────
//  Helpers
// ──────────────────────────────────────────────────────────

const STATUT_CONFIG: Record<StatutEvaluation, { label: string; cls: string }> = {
  BROUILLON: { label: "Brouillon", cls: "bg-muted text-muted-foreground border-transparent"                          },
  PUBLIEE:   { label: "Publiée",   cls: "bg-primary/10 text-primary border-primary/20"                              },
  EN_COURS:  { label: "En cours",  cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"        },
  TERMINEE:  { label: "Terminée",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400" },
}

function StatutBadge({ statut }: { statut: StatutEvaluation }) {
  const { label, cls } = STATUT_CONFIG[statut]
  return (
    <Badge variant="outline" className={`rounded-full px-3 py-1 text-xs font-semibold ${cls}`}>
      {label}
    </Badge>
  )
}

// ──────────────────────────────────────────────────────────
//  Composant principal
// ──────────────────────────────────────────────────────────

export default function DashboardEnseignant() {
  const { user } = useAuth()

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-7">

      {/* En-tête */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-3xl font-extrabold tracking-tight">
            Bonjour, {user?.prenom} <span className="text-2xl">👋</span>
          </h1>
          <p className="mt-1 text-muted-foreground">
            Voici un aperçu de vos évaluations et des actions en attente.
          </p>
        </div>
        <Button asChild className="rounded-xl">
          <Link to="/enseignant/evaluations/creation">
            <Plus className="size-4" /> Nouvelle évaluation
          </Link>
        </Button>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-4">
        {stats.map((s) => (
          <Card key={s.label} className="rounded-2xl border-border/60 shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {s.label}
              </CardTitle>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <s.icon className="size-4" />
              </span>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-extrabold tracking-tight">{s.valeur}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-[1.4fr_1fr]">

        {/* Évaluations récentes */}
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-base font-bold">Mes évaluations</CardTitle>
            <Link
              to="/enseignant/evaluations"
              className="flex items-center gap-1 text-xs text-primary"
            >
              Tout voir <ArrowRight className="size-3" />
            </Link>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 p-0 px-6 pb-4">
            {evaluationsRecentes.map((e, i, arr) => (
              <div
                key={e.id}
                className={`flex items-center gap-3 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
                  <FileText className="size-[18px]" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13px] font-semibold">{e.titre}</p>
                  <p className="mt-0.5 text-[11px] text-muted-foreground">
                    {e.nbSoumissions} / {e.nbAttendus} soumissions
                  </p>
                </div>
                <StatutBadge statut={e.statut} />
              </div>
            ))}
          </CardContent>
        </Card>

        {/* Corrections IA en attente */}
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-base font-bold">Corrections IA</CardTitle>
            <Link
              to="/enseignant/validation-ia"
              className="flex items-center gap-1 text-xs text-primary"
            >
              Valider <ArrowRight className="size-3" />
            </Link>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 p-0 px-6 pb-4">
            {correctionsEnAttente.map((c, i, arr) => (
              <div
                key={c.id}
                className={`flex items-center gap-3 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
                  <CheckCircle2 className="size-[18px]" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13px] font-semibold">{c.etudiant}</p>
                  <p className="mt-0.5 truncate text-[11px] text-muted-foreground">
                    {c.question}
                  </p>
                </div>
                <span className="shrink-0 text-[13px] font-bold text-primary">
                  {c.noteIA} / 5
                </span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      {/* Accès rapides */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-bold">Accès rapides</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 md:grid-cols-3">
            {[
              { icon: Eye,         label: "Supervision en cours",  to: "/enseignant/supervision",   desc: "Suivre les passations live"     },
              { icon: BarChart3,   label: "Statistiques",          to: "/enseignant/statistiques",  desc: "Analyser les résultats"         },
              { icon: Clock,       label: "Publier les résultats", to: "/enseignant/publication",   desc: "Notifier la promotion"          },
            ].map((a) => (
              <Link
                key={a.to}
                to={a.to}
                className="flex items-center gap-3 rounded-xl border border-border p-3.5 transition-colors hover:bg-muted/50"
              >
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <a.icon className="size-5" />
                </span>
                <div>
                  <div className="text-[13px] font-semibold">{a.label}</div>
                  <div className="text-[11px] text-muted-foreground">{a.desc}</div>
                </div>
              </Link>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
