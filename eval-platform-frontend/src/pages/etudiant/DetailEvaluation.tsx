import { useParams, useNavigate, Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import {
  CLE_MES_EVALUATIONS,
  cheminPassation,
  etatEvaluation,
  formatDateHeure,
  mesEvaluations,
  typeEpreuve,
  type TypeEpreuve,
} from "@/lib/evaluationsEtudiant"
import { messageErreur } from "@/lib/passation"
import {
  Clock,
  ListChecks,
  Calendar,
  FileText,
  Save,
  ShieldAlert,
  ArrowLeft,
  Play,
  Timer,
} from "lucide-react"

// Libellés lisibles par type d'épreuve
const typeLabels: Record<TypeEpreuve, string> = {
  QCM: "QCM",
  CODE: "Exercice de code",
  OUVERTE: "Questions ouvertes",
  QUIZ: "Quiz chronométré",
  MIXTE: "Épreuve mixte",
}

function Retour() {
  return (
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
  )
}

export default function DetailEvaluation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  // Même requête que la liste (mise en cache) : le détail d'une évaluation en est extrait
  const { data: evaluations, isPending, error } = useQuery({
    queryKey: CLE_MES_EVALUATIONS,
    queryFn: mesEvaluations,
  })
  const evaluation = evaluations?.find((e) => String(e.id) === id)

  if (!evaluation) {
    const message = isPending
      ? "Chargement de l'évaluation..."
      : error
        ? messageErreur(error, "Impossible de charger l'évaluation.")
        : "Cette évaluation est introuvable."
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="py-10 text-center text-sm text-muted-foreground">{message}</CardContent>
        </Card>
        <Retour />
      </div>
    )
  }

  const type = typeEpreuve(evaluation)
  const etat = etatEvaluation(evaluation)
  const chemin = cheminPassation(evaluation)
  const enCours = evaluation.statutSoumission === "EN_COURS"

  const infos = [
    { label: "Durée", valeur: `${evaluation.dureeMinutes} min`, icon: Clock },
    { label: "Questions", valeur: `${evaluation.nbQuestions}`, icon: ListChecks },
    { label: "Barème", valeur: `${evaluation.totalPoints} pts`, icon: FileText },
    { label: "Date limite", valeur: formatDateHeure(evaluation.dateFermeture), icon: Calendar },
  ]

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <Retour />

      {/* En-tête */}
      <div className="flex flex-col gap-2">
        <Badge
          variant="outline"
          className="w-fit rounded-full border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary"
        >
          {typeLabels[type]}
        </Badge>
        <h1 className="text-3xl font-extrabold tracking-tight">{evaluation.titre}</h1>
        {evaluation.description && <p className="text-muted-foreground">{evaluation.description}</p>}
      </div>

      {/* Infos clés */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {infos.map((info) => (
          <Card key={info.label} className="rounded-2xl border-border/60 shadow-sm">
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
          <CardTitle className="text-base font-bold">Avant de commencer</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3 text-sm text-muted-foreground">
          <div className="flex items-start gap-3">
            <FileText className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>
              {evaluation.nbQuestions} question{evaluation.nbQuestions > 1 ? "s" : ""} pour un total de{" "}
              {evaluation.totalPoints} points.
            </span>
          </div>
          {type === "QUIZ" && evaluation.dureeQuestion !== null && (
            <div className="flex items-start gap-3">
              <Timer className="mt-0.5 size-4 shrink-0 text-primary" />
              <span>
                Chaque question dispose de {evaluation.dureeQuestion} secondes ; une question passée ne peut
                plus être modifiée.
              </span>
            </div>
          )}
          <div className="flex items-start gap-3">
            <Save className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>Vos réponses sont sauvegardées automatiquement, même en cas de coupure réseau.</span>
          </div>
          <div className="flex items-start gap-3">
            <Clock className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>
              Le chronomètre démarre dès que vous cliquez sur « Démarrer » et ne peut pas être mis en pause.
            </span>
          </div>
          <div className="flex items-start gap-3">
            <ShieldAlert className="mt-0.5 size-4 shrink-0 text-primary" />
            <span>
              Tout changement d'onglet ou de fenêtre est enregistré. Restez sur la page pendant toute l'épreuve.
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Avertissement */}
      <Alert className="rounded-2xl border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400">
        <ShieldAlert className="size-4" />
        <AlertTitle>Attention</AlertTitle>
        <AlertDescription>
          Une fois l'évaluation démarrée, le temps s'écoule sans interruption. À expiration, votre copie est
          soumise automatiquement dans son état actuel.
        </AlertDescription>
      </Alert>

      {/* Action principale — active uniquement si l'évaluation est disponible et qu'un écran sait l'afficher */}
      {etat === "disponible" && chemin ? (
        <Button size="lg" className="w-full shadow-md sm:w-auto sm:self-end" onClick={() => navigate(chemin)}>
          <Play className="mr-1 size-4" />
          {enCours ? "Reprendre l'évaluation" : "Démarrer l'évaluation"}
        </Button>
      ) : (
        <div className="rounded-xl border border-border/60 bg-muted/50 px-4 py-3 text-center text-sm text-muted-foreground">
          {etat === "a_venir"
            ? `Cette évaluation ouvre le ${formatDateHeure(evaluation.dateOuverture)}.`
            : etat === "terminee"
              ? "Cette évaluation est terminée."
              : "Épreuve mixte : l'écran qui réunit plusieurs types de questions est en préparation."}
        </div>
      )}
    </div>
  )
}
