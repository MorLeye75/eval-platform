import { useParams, useNavigate, Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
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

// Type d'affichage local — à harmoniser avec src/types quand le backend sera branché
interface DetailEval {
  id: number
  titre: string
  description: string
  type: string
  duree: number // minutes
  nbQuestions: number
  dateLimite: string
  points: number
}

// Données mockées — à remplacer par un appel API (React Query) quand le backend sera prêt
const evaluationsMock: Record<number, DetailEval> = {
  1: {
    id: 1,
    titre: "Structures de données — QCM",
    description:
      "Évaluation portant sur les listes, piles, files, arbres et tables de hachage. Lisez attentivement chaque question avant de répondre.",
    type: "QCM",
    duree: 45,
    nbQuestions: 20,
    dateLimite: "12 juil. 2026 à 23h59",
    points: 20,
  },
  2: {
    id: 2,
    titre: "Programmation C — exercice de code",
    description:
      "Exercices de programmation en C avec exécution automatique contre des cas de test.",
    type: "Exercice de code",
    duree: 90,
    nbQuestions: 3,
    dateLimite: "15 juil. 2026 à 23h59",
    points: 20,
  },
  3: {
    id: 3,
    titre: "Réseaux — questions ouvertes",
    description:
      "Questions ouvertes sur les protocoles réseau, l'adressage IP et le routage.",
    type: "Questions ouvertes",
    duree: 60,
    nbQuestions: 5,
    dateLimite: "5 juil. 2026 à 23h59",
    points: 20,
  },
  4: {
    id: 4,
    titre: "Bases de données — quiz chronométré",
    description:
      "Quiz sur le modèle relationnel, SQL et la normalisation. Chaque question a une durée limitée.",
    type: "Quiz chronométré",
    duree: 30,
    nbQuestions: 15,
    dateLimite: "10 juil. 2026 à 18h00",
    points: 15,
  },
}

export default function DetailEvaluation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const evaluation = id ? evaluationsMock[Number(id)] : undefined

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

  const infos = [
    { label: "Durée", valeur: `${evaluation.duree} min`, icon: Clock },
    { label: "Questions", valeur: `${evaluation.nbQuestions}`, icon: ListChecks },
    { label: "Points", valeur: `${evaluation.points} pts`, icon: FileText },
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
          {evaluation.type}
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

      {/* Action principale */}
      <Button
        size="lg"
        className="w-full shadow-md sm:w-auto sm:self-end"
        onClick={() => navigate(`/examen/${evaluation.id}/qcm`)}
      >
        <Play className="mr-1 size-4" />
        Démarrer l'évaluation
      </Button>
    </div>
  )
}