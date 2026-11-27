import { useQuery } from "@tanstack/react-query"
import { Eye, Users, AlertTriangle, FileText } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import type { NiveauAlerte } from "@/types"
import { StatCard } from "@/components/admin/AdminWidgets"
import { CLE_ADMIN_SUPERVISION, INTERVALLE_SUPERVISION_GLOBALE_MS, supervisionGlobale, type AlerteGlobaleApi } from "@/lib/adminVue"
import { messageErreur } from "@/lib/passation"
import { pourcentage } from "@/lib/supervision"

// Supervision globale (DS-18) : toutes les évaluations en cours de la plateforme, relues toutes les 30 s. Lecture seule ;
// les alertes sont des indices, pas des preuves.

const ALERTE_CLS: Record<NiveauAlerte, string> = {
  INFO: "bg-muted text-muted-foreground",
  MODERE: "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400",
  ELEVE: "bg-destructive/10 text-destructive",
}
const ALERTE_LABEL: Record<NiveauAlerte, string> = { INFO: "Info", MODERE: "Modéré", ELEVE: "Élevé" }

export default function SupervisionGlobale() {
  const requete = useQuery({
    queryKey: CLE_ADMIN_SUPERVISION,
    queryFn: supervisionGlobale,
    refetchInterval: INTERVALLE_SUPERVISION_GLOBALE_MS,
  })
  const d = requete.data
  const commences = d?.evaluations.reduce((n, e) => n + e.nbCommences, 0)
  const attendus = d?.evaluations.reduce((n, e) => n + e.nbAttendus, 0)

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Supervision globale</h1>
        <p className="mt-1 text-muted-foreground">
          Évaluations en cours de passation, actualisées toutes les 30 secondes. Les alertes sont des indices, pas des preuves.
        </p>
      </div>

      {requete.isError && (
        <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {messageErreur(requete.error, "Impossible de charger la supervision.")}
          {d && " Données non actualisées : dernier état connu affiché."}
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        <StatCard label="Évaluations en cours" value={d ? String(d.evaluations.length) : "—"} icon={Eye} />
        <StatCard label="Copies commencées" value={commences === undefined ? "—" : String(commences)} icon={Users}
                  hint={attendus === undefined ? undefined : `sur ${attendus} attendues`} />
        <StatCard label="Alertes récentes" value={d ? String(d.alertes.length) : "—"} icon={AlertTriangle}
                  hint="20 dernières" dir={d && d.alertes.length > 0 ? "down" : undefined} />
      </div>

      <div className="flex flex-col gap-3">
        <h2 className="font-semibold">Évaluations en cours</h2>
        {d && d.evaluations.length === 0 && (
          <p className="text-sm text-muted-foreground">Aucune évaluation n'est en cours actuellement.</p>
        )}
        {(d?.evaluations ?? []).map((e) => {
          const pct = pourcentage(e.nbCommences, e.nbAttendus)
          return (
            <Card key={e.id} className="rounded-2xl border-border/60 shadow-sm">
              <CardContent className="flex items-center gap-4 p-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <FileText className="size-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">{e.titre}</div>
                  <div className="mt-0.5 text-[11px] text-muted-foreground">
                    {e.enseignant} · {e.nbCommences} / {e.nbAttendus} copies commencées · {e.nbSoumis} remise{e.nbSoumis > 1 ? "s" : ""}
                  </div>
                  <Progress value={pct} className="mt-2 h-1.5" />
                </div>
                <div className="shrink-0 text-right">
                  <div className="text-xl font-extrabold">{pct}%</div>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>

      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Alertes anti-triche récentes</CardTitle>
        </CardHeader>
        <CardContent className="px-6 pb-4">
          {d && d.alertes.length === 0 && <p className="py-4 text-sm text-muted-foreground">Aucune alerte.</p>}
          {(d?.alertes ?? []).map((a) => <LigneAlerte key={a.id} alerte={a} />)}
        </CardContent>
      </Card>
    </div>
  )
}

function LigneAlerte({ alerte }: { alerte: AlerteGlobaleApi }) {
  return (
    <div className="flex items-center gap-3 border-b border-border py-3 last:border-none">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
        <AlertTriangle className="size-[18px]" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-semibold">{alerte.description}</div>
        <div className="mt-0.5 text-[11px] text-muted-foreground">{alerte.etudiant} · {alerte.evaluation}</div>
      </div>
      <span className={`inline-flex items-center rounded-full px-2.5 py-[3px] text-[11px] font-semibold ${ALERTE_CLS[alerte.niveau]}`}>
        {ALERTE_LABEL[alerte.niveau]}
      </span>
    </div>
  )
}
