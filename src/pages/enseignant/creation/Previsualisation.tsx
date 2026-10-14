import { useState } from "react"
import { useNavigate, useLocation } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  CheckCircle2,
  Clock,
  Calendar,
  FileText,
  Send,
} from "lucide-react"
import { CreationStepper, type InfosGeneralesForm } from "./InfosGenerales"
import type { StatutEvaluation, TypeQuestion } from "@/types"

// ============================================================
// Étape 3 / 3 — Prévisualisation & publication
// TODO : onPublier → POST /api/enseignant/evaluations { infos, questions }
// ============================================================

const TYPE_LABEL: Record<TypeQuestion, string> = {
  QCM:               "QCM",
  OUVERTE:           "Question ouverte",
  CODE:              "Exercice de code",
  QUIZ_CHRONOMETREE: "Quiz chronométré",
}

const STATUT_CONFIG: Record<StatutEvaluation, { label: string; cls: string }> = {
  BROUILLON: { label: "Brouillon", cls: "bg-muted text-muted-foreground border-transparent"                          },
  PUBLIEE:   { label: "Publiée",   cls: "bg-primary/10 text-primary border-primary/20"                              },
  EN_COURS:  { label: "En cours",  cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"        },
  TERMINEE:  { label: "Terminée",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400"},
}

function formatDT(iso: string) {
  if (!iso) return "—"
  return new Date(iso).toLocaleString("fr-FR", {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  })
}

export default function Previsualisation() {
  const navigate   = useNavigate()
  const location   = useLocation()
  const state      = location.state as { infos: InfosGeneralesForm; questions: any[] } | null
  const infos      = state?.infos
  const questions  = state?.questions ?? []
  const [saving, setSaving] = useState(false)

  const totalPoints = questions.reduce((s: number, q: any) => s + (q.points ?? 0), 0)

  async function handlePublier() {
    setSaving(true)
    try {
      // TODO : await api.post("/enseignant/evaluations", { infos, questions })
      // Redirection après succès
      navigate("/enseignant/evaluations", { replace: true })
    } finally {
      setSaving(false)
    }
  }

  if (!infos) {
    return (
      <div className="flex flex-col items-center gap-4 pt-20 text-center text-muted-foreground">
        <p>Données manquantes. Recommencez depuis le début.</p>
        <Button className="rounded-xl" onClick={() => navigate("/enseignant/evaluations/creation")}>
          Recommencer
        </Button>
      </div>
    )
  }

  const { label: statutLabel, cls: statutCls } = STATUT_CONFIG[infos.statut]

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">

      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Nouvelle évaluation</h1>
        <p className="mt-1 text-muted-foreground">Vérifiez avant de valider.</p>
      </div>

      <CreationStepper step={3} />

      {/* Récapitulatif infos */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <div className="flex items-start justify-between gap-4">
            <CardTitle className="text-base font-bold">{infos.titre}</CardTitle>
            <Badge variant="outline" className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${statutCls}`}>
              {statutLabel}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground">{infos.description}</p>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 md:grid-cols-3">
            <div className="flex items-center gap-2 text-sm">
              <Clock className="size-4 text-primary" />
              <span className="text-muted-foreground">Durée :</span>
              <span className="font-semibold">{infos.duree} min</span>
            </div>
            <div className="flex items-center gap-2 text-sm">
              <Calendar className="size-4 text-primary" />
              <span className="text-muted-foreground">Ouverture :</span>
              <span className="font-semibold">{formatDT(infos.dateOuverture)}</span>
            </div>
            <div className="flex items-center gap-2 text-sm">
              <Calendar className="size-4 text-primary" />
              <span className="text-muted-foreground">Fermeture :</span>
              <span className="font-semibold">{formatDT(infos.dateFermeture)}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Récapitulatif questions */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-bold">
              {questions.length} question{questions.length > 1 ? "s" : ""}
            </CardTitle>
            <span className="text-sm font-bold">{totalPoints} pts au total</span>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-0 p-0 px-6 pb-4">
          {questions.map((q: any, idx: number) => (
            <div
              key={q.localId}
              className="flex items-center gap-3 border-b border-border/60 py-3 last:border-none"
            >
              <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
                <FileText className="size-[18px]" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-semibold">
                  Q{idx + 1}. {q.enonce || <span className="italic text-muted-foreground">Sans énoncé</span>}
                </p>
                <p className="mt-0.5 text-[11px] text-muted-foreground">
                  {TYPE_LABEL[q.type as TypeQuestion]}
                  {q.type === "CODE" && ` · ${q.casTests?.length ?? 0} cas de test`}
                  {q.type === "QCM"  && ` · ${q.choix?.length ?? 0} choix`}
                </p>
              </div>
              <span className="shrink-0 text-sm font-bold">{q.points} pts</span>
            </div>
          ))}
        </CardContent>
      </Card>

      {/* Confirmation */}
      <Card className="rounded-2xl border-primary/20 bg-primary/5 shadow-sm">
        <CardContent className="flex items-center gap-3 p-4">
          <CheckCircle2 className="size-5 shrink-0 text-primary" />
          <p className="text-sm text-muted-foreground">
            {infos.statut === "PUBLIEE"
              ? "L'évaluation sera immédiatement visible par les étudiants à la date d'ouverture."
              : "L'évaluation sera enregistrée en brouillon. Vous pourrez la publier depuis la liste."}
          </p>
        </CardContent>
      </Card>

      {/* Navigation */}
      <div className="flex justify-between gap-3">
        <Button variant="outline" className="rounded-xl" onClick={() => navigate(-1)}>
          ← Modifier les questions
        </Button>
        <Button className="rounded-xl" onClick={handlePublier} disabled={saving}>
          {saving ? "Enregistrement..." : (
            <>
              <Send className="size-4" />
              {infos.statut === "PUBLIEE" ? "Publier l'évaluation" : "Enregistrer en brouillon"}
            </>
          )}
        </Button>
      </div>
    </div>
  )
}
