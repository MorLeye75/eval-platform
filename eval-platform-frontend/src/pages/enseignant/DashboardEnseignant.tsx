import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/context/AuthContext"
import {
  ClipboardList,
  Plus,
  Send,
  FileEdit,
  CheckCircle2,
  Clock,
  ArrowRight,
  FileText,
  Eye,
  BarChart3,
} from "lucide-react"
import type { StatutEvaluation } from "@/types"
import { CLE_EVALUATIONS_ENSEIGNANT, mesEvaluationsEnseignant } from "@/lib/evaluationsEnseignant"
import { CLE_CORRECTIONS_IA, listerCorrectionsIA } from "@/lib/correctionsIA"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Tableau de bord de l'enseignant : synthèse des données déjà exposées (évaluations, corrections IA). Aucune route
// dédiée : chaque chiffre vient de la même source que l'écran qu'il résume, donc ne peut pas le contredire.
// ============================================================

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

export default function DashboardEnseignant() {
  const { user } = useAuth()
  const evaluations = useQuery({ queryKey: CLE_EVALUATIONS_ENSEIGNANT, queryFn: mesEvaluationsEnseignant })
  const corrections = useQuery({ queryKey: CLE_CORRECTIONS_IA, queryFn: listerCorrectionsIA })

  const liste = evaluations.data ?? []
  const enAttente = (corrections.data?.propositions ?? []).filter((p) => p.statut === "EN_ATTENTE")
  const stats = [
    { label: "Évaluations en cours",       valeur: liste.filter((e) => e.statut === "EN_COURS").length, icon: ClipboardList },
    { label: "Résultats à publier",        valeur: liste.filter((e) => e.statut === "TERMINEE" && !e.resultatsPublies).length, icon: Send },
    { label: "Corrections IA en attente",  valeur: enAttente.length, icon: CheckCircle2 },
    { label: "Brouillons",                 valeur: liste.filter((e) => e.statut === "BROUILLON").length, icon: FileEdit },
  ]
  const chargee = evaluations.isSuccess && corrections.isSuccess

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-7">

      {/* En-tête */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-3xl font-extrabold tracking-tight">
            Bonjour, {user?.prenom} <span className="text-2xl">👋</span>
          </h1>
          <p className="mt-1 text-muted-foreground">
            Voici un aperçu de vos évaluations et des actions en attente.
          </p>
        </div>
        <Button
          className="rounded-xl"
          render={
            <Link to="/enseignant/evaluations/creation" state={{ nouveau: true }}>
              <Plus className="size-4" /> Nouvelle évaluation
            </Link>
          }
        />
      </div>

      {(evaluations.isError || corrections.isError) && (
        <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {messageErreur(evaluations.error ?? corrections.error, "Certaines données n'ont pas pu être chargées.")}
        </p>
      )}

      {/* Stats : « — » tant que les données ne sont pas là, jamais un 0 trompeur */}
      <div className="grid gap-4 md:grid-cols-4">
        {stats.map((s) => (
          <Card key={s.label} className="rounded-2xl border-border/60 shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">{s.label}</CardTitle>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <s.icon className="size-4" />
              </span>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-extrabold tracking-tight">{chargee ? s.valeur : "—"}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-[1.4fr_1fr]">

        {/* Évaluations récentes */}
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-base font-bold">Mes évaluations</CardTitle>
            <Link to="/enseignant/evaluations" className="flex items-center gap-1 text-xs text-primary">
              Tout voir <ArrowRight className="size-3" />
            </Link>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 p-0 px-6 pb-4">
            {liste.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                {evaluations.isLoading ? "Chargement…" : "Aucune évaluation pour l'instant."}
              </p>
            ) : (
              liste.slice(0, 4).map((e, i, arr) => (
                <div key={e.id} className={`flex items-center gap-3 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}>
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
                    <FileText className="size-[18px]" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13px] font-semibold">{e.titre}</p>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">
                      {e.nbCopies ?? 0} / {e.nbAttendus ?? 0} copies
                    </p>
                  </div>
                  <StatutBadge statut={e.statut} />
                </div>
              ))
            )}
          </CardContent>
        </Card>

        {/* Corrections IA en attente */}
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-base font-bold">Corrections IA</CardTitle>
            <Link to="/enseignant/validation-ia" className="flex items-center gap-1 text-xs text-primary">
              Valider <ArrowRight className="size-3" />
            </Link>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 p-0 px-6 pb-4">
            {enAttente.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                {corrections.isLoading ? "Chargement…" : "Aucune correction en attente."}
              </p>
            ) : (
              enAttente.slice(0, 3).map((c, i, arr) => (
                <div key={c.id} className={`flex items-center gap-3 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}>
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
                    <CheckCircle2 className="size-[18px]" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13px] font-semibold">{c.etudiant}</p>
                    <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{c.question}</p>
                  </div>
                  <span className="shrink-0 text-[13px] font-bold text-primary">{c.noteProposee} / {c.pointsMax}</span>
                </div>
              ))
            )}
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
              { icon: Eye,       label: "Supervision en cours",  to: "/enseignant/supervision",  desc: "Suivre les passations en cours" },
              { icon: BarChart3, label: "Statistiques",          to: "/enseignant/statistiques", desc: "Analyser les résultats"         },
              { icon: Clock,     label: "Publier les résultats", to: "/enseignant/publication",  desc: "Notifier la promotion"          },
            ].map((a) => (
              <Link key={a.to} to={a.to} className="flex items-center gap-3 rounded-xl border border-border p-3.5 transition-colors hover:bg-muted/50">
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
