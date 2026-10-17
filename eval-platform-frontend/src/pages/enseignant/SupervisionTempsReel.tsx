import { useEffect, useState } from "react"
import { Link, useParams } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Users, Clock, AlertTriangle, CheckCircle2, FileText } from "lucide-react"
import type { AlerteAntiTriche, NiveauAlerte } from "@/types"
import { evaluationsEnseignantMock, getEvaluationEnseignant } from "@/mocks/evaluationsEnseignant"

// ============================================================
// Données mockées — à remplacer par :
// const { data } = useQuery({
//   queryKey: ["supervision", id],
//   queryFn: () => api.get(`/enseignant/supervision/${id}`),
//   refetchInterval: 30_000,   // polling 30s
// })
// Le badge "live" s'affichera une fois le polling réel branché.
// ============================================================

// Progression par étudiant (mockée) — échantillon générique, le backend
// renverra la liste réelle des étudiants de l'évaluation supervisée.
interface ProgressionEtudiant {
  id: number
  nom: string
  prenom: string
  nbReponses: number
  nbQuestions: number
  statut: "EN_COURS" | "SOUMIS"
  nbAlertes: number
}

const ETUDIANTS: ProgressionEtudiant[] = [
  { id: 1,  nom: "Diop",   prenom: "Moussa",   nbReponses: 8,  nbQuestions: 10, statut: "EN_COURS", nbAlertes: 0 },
  { id: 2,  nom: "Ndiaye", prenom: "Awa",       nbReponses: 10, nbQuestions: 10, statut: "SOUMIS",   nbAlertes: 0 },
  { id: 3,  nom: "Ba",     prenom: "Cheikh",    nbReponses: 5,  nbQuestions: 10, statut: "EN_COURS", nbAlertes: 2 },
  { id: 4,  nom: "Sy",     prenom: "Mariama",   nbReponses: 10, nbQuestions: 10, statut: "SOUMIS",   nbAlertes: 0 },
  { id: 5,  nom: "Fall",   prenom: "Ibrahima",  nbReponses: 3,  nbQuestions: 10, statut: "EN_COURS", nbAlertes: 1 },
  { id: 6,  nom: "Mbaye",  prenom: "Sokhna",    nbReponses: 10, nbQuestions: 10, statut: "SOUMIS",   nbAlertes: 0 },
  { id: 7,  nom: "Diouf",  prenom: "Aminata",   nbReponses: 7,  nbQuestions: 10, statut: "EN_COURS", nbAlertes: 0 },
  { id: 8,  nom: "Gaye",   prenom: "Oumar",     nbReponses: 10, nbQuestions: 10, statut: "SOUMIS",   nbAlertes: 0 },
]

const ALERTES: AlerteAntiTriche[] = [
  { id: 1, etudiantId: 3, evaluationId: 1, description: "Changement d'onglet répété (3 fois)", niveau: "MODERE", date: "2026-07-20T08:22:00" },
  { id: 2, etudiantId: 5, evaluationId: 1, description: "Perte de focus prolongée — 90 secondes", niveau: "MODERE", date: "2026-07-20T08:31:00" },
  { id: 3, etudiantId: 3, evaluationId: 1, description: "Changement d'onglet supplémentaire", niveau: "ELEVE",  date: "2026-07-20T08:38:00" },
]

const ALERTE_CLS: Record<NiveauAlerte, string> = {
  INFO:   "bg-muted text-muted-foreground",
  MODERE: "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400",
  ELEVE:  "bg-destructive/10 text-destructive",
}

const ALERTE_LABEL: Record<NiveauAlerte, string> = {
  INFO: "Info", MODERE: "Modéré", ELEVE: "Élevé",
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })
}

function getTempsRestant(dateFin: string, maintenant: number) {
  const diff = new Date(dateFin).getTime() - maintenant
  if (diff <= 0) return "Terminé"
  const min = Math.floor(diff / 60000)
  const sec = Math.floor((diff % 60000) / 1000)
  return `${min}m ${sec}s`
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

export default function SupervisionTempsReel() {
  const { id } = useParams<{ id: string }>()
  const maintenant = useMaintenant()
  // Sans id (lien du menu) : première évaluation en cours
  const e = id
    ? getEvaluationEnseignant(id)
    : evaluationsEnseignantMock.find((ev) => ev.statut === "EN_COURS")

  if (!e) {
    return (
      <div className="mx-auto flex max-w-2xl flex-col items-center gap-4 py-10 text-center">
        <Card className="w-full rounded-2xl border-border/60 shadow-sm">
          <CardContent className="py-10 text-sm text-muted-foreground">
            {id ? "Cette évaluation est introuvable." : "Aucune évaluation n'est en cours actuellement."}
          </CardContent>
        </Card>
        <Button
          variant="outline"
          render={<Link to="/enseignant/evaluations">Retour aux évaluations</Link>}
        />
      </div>
    )
  }

  const alertes = ALERTES.filter((a) => a.evaluationId === e.id)
  const pct = e.nbAttendus > 0 ? Math.round((e.nbSoumissions / e.nbAttendus) * 100) : 0
  const nbSoumis  = e.nbSoumissions
  const nbEnCours = e.nbAttendus - e.nbSoumissions

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">

      {/* En-tête */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Supervision en temps réel</h1>
        <p className="mt-1 text-muted-foreground">{e.titre}</p>
      </div>

      {/* Stats globales */}
      <div className="grid gap-4 md:grid-cols-4">
        {[
          { label: "Étudiants en cours", value: String(nbEnCours),       icon: Users,        dir: undefined   },
          { label: "Soumissions reçues", value: String(nbSoumis),        icon: CheckCircle2, dir: "up" as const },
          { label: "Alertes détectées",  value: String(alertes.length),  icon: AlertTriangle,dir: alertes.length > 0 ? "down" as const : undefined },
          { label: "Temps restant",      value: getTempsRestant(e.dateFermeture, maintenant), icon: Clock,     dir: undefined   },
        ].map((s) => (
          <Card key={s.label} className="rounded-2xl border-border/60 shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">{s.label}</CardTitle>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <s.icon className="size-4" />
              </span>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-extrabold tracking-tight">{s.value}</div>
              {s.dir && (
                <p className={`mt-0.5 text-[11px] ${s.dir === "up" ? "text-primary" : "text-destructive"}`}>
                  {s.dir === "up" ? `${pct}% de complétion` : "à surveiller"}
                </p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Progression globale */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex items-center gap-4 p-4">
          <FileText className="size-5 shrink-0 text-primary" />
          <div className="flex-1">
            <div className="mb-1 flex justify-between text-sm">
              <span className="font-semibold">{e.nbSoumissions} soumissions reçues</span>
              <span className="text-muted-foreground">sur {e.nbAttendus} attendues</span>
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
              {ETUDIANTS.map((et) => {
                const pctEt = Math.round((et.nbReponses / et.nbQuestions) * 100)
                const initiales = `${et.prenom[0]}${et.nom[0]}`.toUpperCase()
                return (
                  <div key={et.id} className="flex items-center gap-3 px-4 py-3">
                    <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                      {initiales}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-[13px] font-semibold">{et.prenom} {et.nom}</span>
                        {et.nbAlertes > 0 && (
                          <AlertTriangle className="size-3.5 text-destructive" />
                        )}
                      </div>
                      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
                        <div
                          className={`h-full rounded-full ${et.statut === "SOUMIS" ? "bg-primary" : "bg-blue-500"}`}
                          style={{ width: `${pctEt}%` }}
                        />
                      </div>
                    </div>
                    <Badge
                      variant="outline"
                      className={`shrink-0 rounded-full text-[11px] font-semibold ${
                        et.statut === "SOUMIS"
                          ? "bg-primary/10 text-primary border-primary/20"
                          : "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"
                      }`}
                    >
                      {et.statut === "SOUMIS" ? "Soumis" : `${et.nbReponses}/${et.nbQuestions}`}
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
            <CardTitle className="text-base font-bold">
              Alertes anti-triche
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-0 px-4 pb-4">
            {alertes.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-8 text-center text-muted-foreground">
                <CheckCircle2 className="size-6 text-primary" />
                <p className="text-sm">Aucune alerte détectée.</p>
              </div>
            ) : (
              alertes.map((a, i, arr) => {
                const etudiant = ETUDIANTS.find(et => et.id === a.etudiantId)
                return (
                  <div key={a.id} className={`flex items-start gap-3 py-3 ${i !== arr.length - 1 ? "border-b border-border/60" : ""}`}>
                    <AlertTriangle className={`mt-0.5 size-4 shrink-0 ${a.niveau === "ELEVE" ? "text-destructive" : "text-yellow-600"}`} />
                    <div className="min-w-0 flex-1">
                      <p className="text-[13px] font-semibold">
                        {etudiant ? `${etudiant.prenom} ${etudiant.nom}` : `Étudiant #${a.etudiantId}`}
                      </p>
                      <p className="mt-0.5 text-[11px] text-muted-foreground">{a.description}</p>
                      <p className="mt-0.5 text-[10px] text-muted-foreground">{formatTime(a.date)}</p>
                    </div>
                    <Badge variant="outline" className={`shrink-0 rounded-full text-[11px] font-semibold ${ALERTE_CLS[a.niveau]}`}>
                      {ALERTE_LABEL[a.niveau]}
                    </Badge>
                  </div>
                )
              })
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
