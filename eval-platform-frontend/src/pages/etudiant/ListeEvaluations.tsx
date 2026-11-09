import { useState } from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  CLE_MES_EVALUATIONS,
  etatEvaluation,
  formatDateHeure,
  mesEvaluations,
  typeEpreuve,
  type EtatEvaluation,
  type TypeEpreuve,
} from "@/lib/evaluationsEtudiant"
import { messageErreur } from "@/lib/passation"
import type { EvaluationEtudiant } from "@/types"
import {
  Clock,
  Calendar,
  ListChecks,
  Code2,
  FileText,
  Timer,
  Layers,
  ArrowRight,
} from "lucide-react"

// Config par type d'épreuve (icône + libellé)
const typeConfig: Record<TypeEpreuve, { label: string; icon: typeof ListChecks }> = {
  QCM: { label: "QCM", icon: ListChecks },
  CODE: { label: "Exercice de code", icon: Code2 },
  OUVERTE: { label: "Questions ouvertes", icon: FileText },
  QUIZ: { label: "Quiz chronométré", icon: Timer },
  MIXTE: { label: "Épreuve mixte", icon: Layers },
}

// Config par état (libellé + style du badge)
const etatConfig: Record<EtatEvaluation, { label: string; className: string }> = {
  disponible: {
    label: "Disponible",
    className: "bg-primary/10 text-primary border-primary/20",
  },
  a_venir: {
    label: "À venir",
    className: "bg-amber-500/10 text-amber-700 border-amber-500/20 dark:text-amber-400",
  },
  terminee: {
    label: "Terminée",
    className: "bg-muted text-muted-foreground border-transparent",
  },
}

function EvaluationCard({ evaluation }: { evaluation: EvaluationEtudiant }) {
  const type = typeConfig[typeEpreuve(evaluation)]
  const etat = etatEvaluation(evaluation)
  const statut = etatConfig[etat]
  const TypeIcon = type.icon
  const enCours = evaluation.statutSoumission === "EN_COURS"

  // Note affichée uniquement si les résultats sont publiés (le serveur ne la renvoie pas avant)
  const information =
    evaluation.noteFinale !== null
      ? `Note : ${evaluation.noteFinale} / ${evaluation.totalPoints}`
      : etat === "terminee"
        ? evaluation.statutSoumission
          ? "En attente des résultats"
          : "Non passée"
        : etat === "a_venir"
          ? `Ouvre le ${formatDateHeure(evaluation.dateOuverture)}`
          : `Jusqu'au ${formatDateHeure(evaluation.dateFermeture)}`

  return (
    <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm transition-shadow hover:shadow-md">
      <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10">
            <TypeIcon className="size-5 text-primary" />
          </div>
          <div className="flex min-w-0 flex-col gap-1">
            <p className="truncate font-semibold">{evaluation.titre}</p>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
              <span>{type.label}</span>
              <span className="flex items-center gap-1">
                <Clock className="size-3.5" />
                {evaluation.dureeMinutes} min
              </span>
              <span className="flex items-center gap-1">
                <Calendar className="size-3.5" />
                {information}
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 sm:flex-col sm:items-end lg:flex-row lg:items-center">
          <Badge
            variant="outline"
            className={`rounded-full px-3 py-1 text-xs font-semibold ${statut.className}`}
          >
            {statut.label}
          </Badge>

          {etat === "disponible" && (
            <Button
              size="sm"
              className="shadow-sm"
              render={
                <Link to={`/etudiant/evaluations/${evaluation.id}`}>
                  {enCours ? "Reprendre" : "Démarrer"}
                  <ArrowRight className="ml-1 size-4" />
                </Link>
              }
            />
          )}
          {etat === "a_venir" && (
            <Button
              size="sm"
              variant="outline"
              render={<Link to={`/etudiant/evaluations/${evaluation.id}`}>Voir les détails</Link>}
            />
          )}
          {etat === "terminee" && evaluation.resultatsPublies && evaluation.soumissionId != null && (
            <Button
              size="sm"
              variant="ghost"
              render={<Link to={`/etudiant/resultats/${evaluation.soumissionId}`}>Voir mes résultats</Link>}
            />
          )}
        </div>
      </CardContent>
    </Card>
  )
}

export default function ListeEvaluations() {
  const [filtre, setFiltre] = useState<"toutes" | EtatEvaluation>("toutes")
  const { data: evaluations = [], isPending, error } = useQuery({
    queryKey: CLE_MES_EVALUATIONS,
    queryFn: mesEvaluations,
  })

  const evaluationsFiltrees =
    filtre === "toutes" ? evaluations : evaluations.filter((e) => etatEvaluation(e) === filtre)

  let message: string | null = null
  if (isPending) message = "Chargement de vos évaluations..."
  else if (error) message = messageErreur(error, "Impossible de charger vos évaluations.")
  else if (evaluationsFiltrees.length === 0) message = "Aucune évaluation dans cette catégorie."

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-7">
      {/* En-tête */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Mes évaluations</h1>
        <p className="mt-1 text-muted-foreground">
          Consultez et démarrez vos évaluations disponibles.
        </p>
      </div>

      {/* Filtres par état */}
      <Tabs value={filtre} onValueChange={(v) => setFiltre(v as typeof filtre)}>
        <TabsList className="rounded-xl">
          <TabsTrigger value="toutes">Toutes</TabsTrigger>
          <TabsTrigger value="disponible">Disponibles</TabsTrigger>
          <TabsTrigger value="a_venir">À venir</TabsTrigger>
          <TabsTrigger value="terminee">Terminées</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Liste */}
      <div className="flex flex-col gap-3">
        {message ? (
          <Card className="rounded-2xl border-border/60 shadow-sm">
            <CardContent className="py-10 text-center text-sm text-muted-foreground">{message}</CardContent>
          </Card>
        ) : (
          evaluationsFiltrees.map((evaluation) => (
            <EvaluationCard key={evaluation.id} evaluation={evaluation} />
          ))
        )}
      </div>
    </div>
  )
}
