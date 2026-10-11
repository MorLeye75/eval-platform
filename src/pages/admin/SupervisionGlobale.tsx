import { Eye, Users, AlertTriangle, FileText, Clock } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import type { EvaluationEnCours, AlerteAntTriche, NiveauAlerte } from "@/types"
import { StatCard } from "@/components/admin/AdminWidgets"

/* ---------------------------------------------------------------
   Données mockées — à remplacer par :
   const { data } = useQuery({
     queryKey: ["admin-supervision"],
     queryFn: () => api.get("/admin/supervision"),
     refetchInterval: 30_000,   // polling toutes les 30s
   })
   Le badge "live" s'affichera une fois le polling réel branché.
--------------------------------------------------------------- */
const MOCK_EVALS: EvaluationEnCours[] = [
  { id: 1, titre: "Structures de données — QCM",      enseignant: { nom: "Sarr",  prenom: "Fatou"   }, nbSoumis: 42, nbAttendus: 45, dateDebut: "2026-07-20T08:00:00", dateFin: "2026-07-20T09:00:00" },
  { id: 2, titre: "Algorithmique — exercice de code", enseignant: { nom: "Fall",  prenom: "Ibrahima" }, nbSoumis: 28, nbAttendus: 40, dateDebut: "2026-07-20T09:00:00", dateFin: "2026-07-20T10:30:00" },
  { id: 3, titre: "Réseaux — questions ouvertes",     enseignant: { nom: "Mbaye", prenom: "Sokhna"  }, nbSoumis: 15, nbAttendus: 38, dateDebut: "2026-07-20T10:00:00", dateFin: "2026-07-20T11:00:00" },
]

const MOCK_ALERTES: AlerteAntTriche[] = [
  { id: 1, etudiantId: 217, evaluationId: 3, description: "Changement d'onglet répété (4 fois)",  niveau: "MODERE", date: "2026-07-20T10:42:00" },
  { id: 2, etudiantId: 142, evaluationId: 2, description: "Perte de focus prolongée — 2 minutes", niveau: "ELEVE",  date: "2026-07-20T09:38:00" },
  { id: 3, etudiantId: 89,  evaluationId: 1, description: "Soumission auto à expiration du timer", niveau: "INFO",   date: "2026-07-20T08:58:00" },
]

const ALERTE_CLS: Record<NiveauAlerte, string> = {
  INFO:    "bg-muted text-muted-foreground",
  MODERE:  "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400",
  ELEVE:   "bg-destructive/10 text-destructive",
}
const ALERTE_LABEL: Record<NiveauAlerte, string> = {
  INFO: "Info", MODERE: "Modéré", ELEVE: "Élevé",
}

export default function SupervisionGlobale() {
  const nbConnectes = MOCK_EVALS.reduce((acc, e) => acc + e.nbSoumis, 0)
  const nbAttendus  = MOCK_EVALS.reduce((acc, e) => acc + e.nbAttendus, 0)

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Supervision en temps réel</h1>
        <p className="mt-1 text-muted-foreground">Suivi des évaluations actuellement en cours de passation.</p>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-3">
        <StatCard label="Évaluations en cours" value={String(MOCK_EVALS.length)} icon={Eye}           hint="en cours"        dir="up"   />
        <StatCard label="Étudiants connectés"  value={String(nbConnectes)}        icon={Users}         hint={`sur ${nbAttendus} attendus`}        />
        <StatCard label="Alertes anti-triche"  value={String(MOCK_ALERTES.length)} icon={AlertTriangle} hint="à surveiller"  dir="down" />
      </div>

      {/* Évaluations */}
      <div className="flex flex-col gap-3">
        <h2 className="font-semibold">Évaluations en cours</h2>
        {MOCK_EVALS.map((e) => {
          const pct = e.nbAttendus > 0 ? Math.round((e.nbSoumis / e.nbAttendus) * 100) : 0
          return (
            <Card key={e.id} className="rounded-2xl border-border/60 shadow-sm">
              <CardContent className="flex items-center gap-4 p-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <FileText className="size-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">{e.titre}</div>
                  <div className="mt-0.5 text-[11px] text-muted-foreground">
                    {e.enseignant.prenom} {e.enseignant.nom} · {e.nbSoumis} / {e.nbAttendus} soumissions
                  </div>
                  {/* shadcn Progress */}
                  <Progress value={pct} className="mt-2 h-1.5" />
                </div>
                <div className="shrink-0 text-right">
                  <div className="text-xl font-extrabold">{pct}%</div>
                  <button className="mt-1.5 inline-flex items-center gap-1.5 rounded-lg border border-border bg-background px-2.5 py-1.5 text-[11px] font-semibold transition-colors hover:bg-muted">
                    <Eye className="size-3.5" /> Détails
                  </button>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>

      {/* Alertes */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Alertes anti-triche récentes</CardTitle>
        </CardHeader>
        <CardContent className="px-6 pb-4">
          {MOCK_ALERTES.map((a) => (
            <AlertRow key={a.id} alerte={a} />
          ))}
        </CardContent>
      </Card>
    </div>
  )
}

/* ── Sous-composants ───────────────────────────────────────── */

function AlertRow({ alerte }: { alerte: AlerteAntTriche }) {
  const IconComp = alerte.niveau === "INFO" ? Clock : AlertTriangle
  return (
    <div className="flex items-center gap-3 border-b border-border py-3 last:border-none">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
        <IconComp className="size-[18px]" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-semibold">{alerte.description}</div>
        <div className="mt-0.5 text-[11px] text-muted-foreground">
          Étudiant #{alerte.etudiantId} · Éval #{alerte.evaluationId}
        </div>
      </div>
      <span className={`inline-flex items-center rounded-full px-2.5 py-[3px] text-[11px] font-semibold ${ALERTE_CLS[alerte.niveau]}`}>
        {ALERTE_LABEL[alerte.niveau]}
      </span>
    </div>
  )
}
