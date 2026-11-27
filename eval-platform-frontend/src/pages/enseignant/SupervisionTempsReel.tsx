import { useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Users, Clock, AlertTriangle, CheckCircle2, FileText } from "lucide-react"
import type { NiveauAlerte } from "@/types"
import { CLE_EVALUATIONS_ENSEIGNANT, mesEvaluationsEnseignant } from "@/lib/evaluationsEnseignant"
import {
  INTERVALLE_SUPERVISION_MS,
  cleSupervision,
  pourcentage,
  supervisionEvaluation,
  tempsRestant,
  type EtatCopie,
} from "@/lib/supervision"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Supervision d'une évaluation ouverte (DS-17) : lecture seule, relue toutes les 15 s tant qu'elle est EN_COURS.
// Les alertes sont des indices, pas des preuves : la décision reste à l'enseignant.
// ============================================================

const ALERTE_CLS: Record<NiveauAlerte, string> = {
  INFO:   "bg-muted text-muted-foreground",
  MODERE: "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400",
  ELEVE:  "bg-destructive/10 text-destructive",
}

const ALERTE_LABEL: Record<NiveauAlerte, string> = { INFO: "Info", MODERE: "Modéré", ELEVE: "Élevé" }

const ETAT_CFG: Record<EtatCopie, { label: string; cls: string }> = {
  NON_COMMENCE: { label: "Pas commencé", cls: "bg-muted text-muted-foreground border-transparent" },
  EN_COURS:     { label: "En cours",     cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400" },
  SOUMIS:       { label: "Rendu",        cls: "bg-primary/10 text-primary border-primary/20" },
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })
}

/** Horloge rafraîchie chaque seconde pour le compte à rebours. */
function useMaintenant() {
  const [maintenant, setMaintenant] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setMaintenant(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return maintenant
}

/** Compte à rebours isolé : seul lui se redessine chaque seconde, pas toute la page. */
function TempsRestant({ dateFin }: { dateFin: string }) {
  const maintenant = useMaintenant()
  return <>{tempsRestant(dateFin, maintenant)}</>
}

export default function SupervisionTempsReel() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const evaluations = useQuery({ queryKey: CLE_EVALUATIONS_ENSEIGNANT, queryFn: mesEvaluationsEnseignant })
  const ouvertes = (evaluations.data ?? []).filter((e) => e.statut === "EN_COURS" || e.statut === "TERMINEE")
  // Sans identifiant (lien du menu) : la première évaluation en cours
  const choisie = id ?? String((ouvertes.find((e) => e.statut === "EN_COURS"))?.id ?? "")

  const supervision = useQuery({
    queryKey: cleSupervision(choisie),
    queryFn: () => supervisionEvaluation(choisie),
    enabled: choisie !== "",
    // Le sondage s'arrête quand l'évaluation n'est plus en cours (DS-05 la passe TERMINEE à la fermeture)
    refetchInterval: (q) => (q.state.data?.statut === "EN_COURS" ? INTERVALLE_SUPERVISION_MS : false),
  })

  if (!id && evaluations.isSuccess && choisie === "") {
    return <Message texte="Aucune évaluation n'est en cours actuellement." />
  }
  if (supervision.isError && !supervision.data) {
    return <Message texte={messageErreur(supervision.error, "Impossible de charger la supervision.")} />
  }
  if (!supervision.data) {
    return <p className="text-sm text-muted-foreground">Chargement…</p>
  }

  const s = supervision.data
  const enCours = s.etudiants.filter((e) => e.etat === "EN_COURS").length
  const pct = pourcentage(s.nbSoumis, s.nbAttendus)
  const terminee = s.statut === "TERMINEE"

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">

      {/* En-tête */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Supervision en temps réel</h1>
          <p className="mt-1 text-muted-foreground">{s.titre}{terminee ? " — évaluation terminée" : ""}</p>
        </div>
        <div className="flex min-w-0 max-w-full items-center gap-2">
          <label htmlFor="supervision-evaluation" className="text-sm font-medium">Évaluation :</label>
          <select
            id="supervision-evaluation"
            value={choisie}
            onChange={(e) => navigate(`/enseignant/supervision/${e.target.value}`)}
            className="h-9 min-w-0 max-w-full rounded-lg border border-input bg-background px-3 text-sm"
          >
            {ouvertes.map((e) => <option key={e.id} value={e.id}>{e.titre}</option>)}
          </select>
        </div>
      </div>

      {supervision.isError && (
        <p role="status" className="rounded-xl bg-yellow-500/10 px-4 py-2.5 text-sm text-yellow-700 dark:text-yellow-400">
          Données non actualisées : la dernière lecture a échoué, l'écran affiche l'état précédent.
        </p>
      )}
      {!terminee && (
        <p className="text-xs text-muted-foreground">Actualisation automatique toutes les {INTERVALLE_SUPERVISION_MS / 1000} secondes.</p>
      )}

      {/* Stats globales */}
      <div className="grid gap-4 md:grid-cols-4">
        <Stat label="Étudiants en cours" value={String(enCours)} icon={Users} />
        <Stat label="Copies rendues" value={String(s.nbSoumis)} icon={CheckCircle2} hint={`${pct}% de complétion`} dir="up" />
        <Stat label="Alertes détectées" value={String(s.nbAlertes)} icon={AlertTriangle}
          hint={s.nbAlertes > 0 ? "à vérifier" : undefined} dir={s.nbAlertes > 0 ? "down" : undefined} />
        <Stat label="Temps restant" value={<TempsRestant dateFin={s.dateFermeture} />} icon={Clock} />
      </div>

      {/* Progression globale */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex items-center gap-4 p-4">
          <FileText className="size-5 shrink-0 text-primary" />
          <div className="flex-1">
            <div className="mb-1 flex justify-between text-sm">
              <span className="font-semibold">{s.nbSoumis} copie{s.nbSoumis > 1 ? "s" : ""} rendue{s.nbSoumis > 1 ? "s" : ""}</span>
              <span className="text-muted-foreground">{s.nbCommences} commencée{s.nbCommences > 1 ? "s" : ""} sur {s.nbAttendus} attendues</span>
            </div>
            <Progress value={pct} className="h-2" />
          </div>
          <span className="text-xl font-extrabold">{pct}%</span>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-[1.5fr_1fr]">

        {/* Tableau étudiants */}
        <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Progression des étudiants</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-y divide-border">
              {s.etudiants.map((et) => {
                const pctEt = pourcentage(et.nbReponses, s.nbQuestions)
                const cfg = ETAT_CFG[et.etat]
                return (
                  <div key={et.etudiantId} className="flex items-center gap-3 px-4 py-3">
                    <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                      {`${et.prenom[0] ?? ""}${et.nom[0] ?? ""}`.toUpperCase()}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-[13px] font-semibold">{et.prenom} {et.nom}</span>
                        {et.nbAlertes > 0 && (
                          <AlertTriangle className="size-3.5 text-destructive" aria-label={`${et.nbAlertes} alerte${et.nbAlertes > 1 ? "s" : ""}`} />
                        )}
                        {et.nbChangementsOnglet > 0 && (
                          <span className="text-[10px] text-muted-foreground">{et.nbChangementsOnglet} sortie{et.nbChangementsOnglet > 1 ? "s" : ""} d'écran</span>
                        )}
                      </div>
                      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
                        <div className={`h-full rounded-full ${et.etat === "SOUMIS" ? "bg-primary" : "bg-blue-500"}`} style={{ width: `${pctEt}%` }} />
                      </div>
                    </div>
                    <Badge variant="outline" className={`shrink-0 rounded-full text-[11px] font-semibold ${cfg.cls}`}>
                      {et.etat === "EN_COURS" ? `${et.nbReponses}/${s.nbQuestions}` : cfg.label}
                    </Badge>
                  </div>
                )
              })}
            </div>
          </CardContent>
        </Card>

        {/* Alertes */}
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Alertes anti-triche</CardTitle>
            <p className="text-[11px] text-muted-foreground">Des indices, pas des preuves : une sortie d'écran peut avoir une cause banale.</p>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 px-4 pb-4">
            {s.alertes.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-8 text-center text-muted-foreground">
                <CheckCircle2 className="size-6 text-primary" />
                <p className="text-sm">Aucune alerte détectée.</p>
              </div>
            ) : (
              s.alertes.map((a, i, arr) => (
                <div key={a.id} className={`flex items-start gap-3 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}>
                  <AlertTriangle className={`mt-0.5 size-4 shrink-0 ${a.niveau === "ELEVE" ? "text-destructive" : "text-yellow-600"}`} />
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] font-semibold">{a.etudiant}</p>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">{a.description}</p>
                    <p className="mt-0.5 text-[10px] text-muted-foreground">{formatTime(a.date)}</p>
                  </div>
                  <Badge variant="outline" className={`shrink-0 rounded-full text-[11px] font-semibold ${ALERTE_CLS[a.niveau]}`}>
                    {ALERTE_LABEL[a.niveau]}
                  </Badge>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

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

function Stat({ label, value, icon: Icon, hint, dir }: {
  label: string; value: React.ReactNode; icon: typeof Users; hint?: string; dir?: "up" | "down"
}) {
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
        {hint && <p className={`mt-0.5 text-[11px] ${dir === "up" ? "text-primary" : "text-destructive"}`}>{hint}</p>}
      </CardContent>
    </Card>
  )
}
