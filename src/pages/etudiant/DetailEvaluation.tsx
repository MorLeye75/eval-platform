import { useParams, useNavigate, Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { getEvaluation, getTotalPoints } from "@/mocks/evaluations"
import {
  Clock,
  ListChecks,
  Calendar,
  FileText,
  Save,
  ShieldAlert,
  ArrowLeft,
  Play,
} from "lucide-react"

// Libellés lisibles par type d'évaluation
const typeLabels: Record<string, string> = {
  QCM: "QCM",
  CODE: "Exercice de code",
  OUVERTE: "Questions ouvertes",
  QUIZ: "Quiz chronométré",
}

// Chemin de passation selon le type
const cheminPassation: Record<string, string> = {
  QCM: "qcm",
  CODE: "code",
  OUVERTE: "ouverte",
  QUIZ: "quiz",
}

export default function DetailEvaluation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const evaluation = getEvaluation(id)

  // Cas où l'évaluation n'existe pas
  if (!evaluation) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            Cette évaluation est introuvable.
          </CardContent>
        </Card>
        <Button
          variant="outline"
          className="self-start"
          render={
            <Link to="/etudiant/evaluations">
              <ArrowLeft className="mr-1 size-4" />
              Retour aux évaluations
            </Link>
          }
        />
      </div>
    )
  }

  const totalPoints = getTotalPoints(evaluation)
  const disponible = evaluation.statut === "disponible"

  const infos = [
    { label: "Durée", valeur: `${evaluation.duree} min`, icon: Clock },
    {
      label: "Questions",
      valeur: `${evaluation.questions.length}`,
      icon: ListChecks,
    },
    { label: "Barème", valeur: `/ ${evaluation.noteStandard}`, icon: FileText },
    { label: "Date limite", valeur: evaluation.dateLimite, icon: Calendar },
  ]

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      {/* Retour */}
      <Button
        variant="ghost"
        size="sm"
        className="self-start text-muted-foreground"
        render={
          <Link to="/etudiant/evaluations">
            <ArrowLeft className="mr-1 size-4" />
            Retour aux évaluations
          </Link>
        }
      />

      {/* En-tête */}
      <div className="flex flex-col gap-2">
        <Badge
          variant="outline"
          className="w-fit rounded-full border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary"
        >
          {typeLabels[evaluation.type]}
        </Badge>
        <h1 className="text-3xl font-extrabold tracking-tight">
          {evaluation.titre}
        </h1>
        <p className="text-muted-foreground">{evaluation.description}</p>
      </div>

      {/* Infos clés */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {infos.map((info) => (
          <Card
            key={info.label}
            className="rounded-2xl border-border/60 shadow-sm"
          >
            <CardContent className="flex flex-col items-center gap-1 py-5 text-center">
              <span className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <info.icon className="size-4" />
              </span>
              <p className="mt-1 text-xs text-muted-foreground">{info.label}</p>
              <p className="text-sm font-semibold">{info.valeur}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Consignes */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader>
          <CardTitle className="text-base font-bold">
            Avant de commencer
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3 text-sm text-muted-foreground">
          <div className="flex items-start gap-3">
            <FileText className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>
              {evaluation.questions.length} question
              {evaluation.questions.length > 1 ? "s" : ""} pour un total de{" "}
              {totalPoints} points, ramenés sur {evaluation.noteStandard}.
            </span>
          </div>
          <div className="flex items-start gap-3">
            <Save className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>
              Vos réponses sont sauvegardées automatiquement toutes les 30
              secondes.
            </span>
          </div>
          <div className="flex items-start gap-3">
            <Clock className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>
              Le chronomètre démarre dès que vous cliquez sur « Démarrer » et ne
              peut pas être mis en pause.
            </span>
          </div>
          <div className="flex items-start gap-3">
            <ShieldAlert className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>
              Tout changement d'onglet ou de fenêtre est enregistré. Restez sur
              la page pendant toute l'épreuve.
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Avertissement */}
      <Alert className="rounded-2xl border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400">
        <ShieldAlert className="size-4" />
        <AlertTitle>Attention</AlertTitle>
        <AlertDescription>
          Une fois l'évaluation démarrée, le temps s'écoule sans interruption. À
          expiration, votre copie est soumise automatiquement dans son état
          actuel.
        </AlertDescription>
      </Alert>

      {/* Action principale — active uniquement si l'évaluation est disponible */}
      {disponible ? (
        <Button
          size="lg"
          className="w-full shadow-md sm:w-auto sm:self-end"
          onClick={() =>
            navigate(
              `/examen/${evaluation.id}/${cheminPassation[evaluation.type]}`
            )
          }
        >
          <Play className="mr-1 size-4" />
          Démarrer l'évaluation
        </Button>
      ) : (
        <div className="rounded-xl border border-border/60 bg-muted/50 px-4 py-3 text-center text-sm text-muted-foreground">
          {evaluation.statut === "a_venir"
            ? "Cette évaluation n'est pas encore ouverte."
            : "Cette évaluation est terminée."}
        </div>
      )}
    </div>
  )
}