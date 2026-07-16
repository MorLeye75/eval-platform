import { useState } from "react"
import { Link } from "react-router-dom"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Clock,
  Calendar,
  ListChecks,
  Code2,
  FileText,
  Timer,
  ArrowRight,
} from "lucide-react"

// Type d'une évaluation (à terme, viendra de src/types/ + API)
type StatutEval = "disponible" | "a_venir" | "terminee"
type TypeEval = "QCM" | "CODE" | "OUVERTE" | "QUIZ"

interface Evaluation {
  id: number
  titre: string
  type: TypeEval
  duree: number // en minutes
  dateLimite: string
  statut: StatutEval
  note?: string // seulement si terminée
}

// Données mockées — à remplacer par un appel API (React Query) quand le backend sera prêt
const evaluations: Evaluation[] = [
  {
    id: 1,
    titre: "Structures de données — QCM",
    type: "QCM",
    duree: 45,
    dateLimite: "12 juil. 2026",
    statut: "disponible",
  },
  {
    id: 2,
    titre: "Programmation C — exercice de code",
    type: "CODE",
    duree: 90,
    dateLimite: "15 juil. 2026",
    statut: "a_venir",
  },
  {
    id: 3,
    titre: "Réseaux — questions ouvertes",
    type: "OUVERTE",
    duree: 60,
    dateLimite: "5 juil. 2026",
    statut: "terminee",
    note: "16 / 20",
  },
  {
    id: 4,
    titre: "Bases de données — quiz chronométré",
    type: "QUIZ",
    duree: 30,
    dateLimite: "10 juil. 2026",
    statut: "disponible",
  },
]

// Config par type de question (icône + libellé)
const typeConfig: Record<TypeEval, { label: string; icon: typeof ListChecks }> = {
  QCM: { label: "QCM", icon: ListChecks },
  CODE: { label: "Exercice de code", icon: Code2 },
  OUVERTE: { label: "Questions ouvertes", icon: FileText },
  QUIZ: { label: "Quiz chronométré", icon: Timer },
}

// Config par statut (libellé + style du badge)
const statutConfig: Record<StatutEval, { label: string; className: string }> = {
  disponible: {
    label: "Disponible",
    className: "bg-primary/10 text-primary border-primary/20",
  },
  a_venir: {
    label: "À venir",
    className:
      "bg-amber-500/10 text-amber-700 border-amber-500/20 dark:text-amber-400",
  },
  terminee: {
    label: "Terminée",
    className: "bg-muted text-muted-foreground",
  },
}

function EvaluationCard({ evaluation }: { evaluation: Evaluation }) {
  const type = typeConfig[evaluation.type]
  const statut = statutConfig[evaluation.statut]
  const TypeIcon = type.icon

  return (
    <Card>
      <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-4">
          <div className="flex size-11 shrink-0 items-center justify-center rounded-lg bg-primary/10">
            <TypeIcon className="size-5 text-primary" />
          </div>
          <div className="flex flex-col gap-1">
            <p className="font-semibold">{evaluation.titre}</p>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
              <span>{type.label}</span>
              <span className="flex items-center gap-1">
                <Clock className="size-3.5" />
                {evaluation.duree} min
              </span>
              <span className="flex items-center gap-1">
                <Calendar className="size-3.5" />
                {evaluation.statut === "terminee"
                  ? `Note : ${evaluation.note}`
                  : `Jusqu'au ${evaluation.dateLimite}`}
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 sm:flex-col sm:items-end lg:flex-row lg:items-center">
          <Badge variant="outline" className={statut.className}>
            {statut.label}
          </Badge>

          {evaluation.statut === "disponible" && (
            <Button asChild size="sm">
              <Link to={`/etudiant/evaluations/${evaluation.id}`}>
                Démarrer
                <ArrowRight className="ml-1 size-4" />
              </Link>
            </Button>
          )}
          {evaluation.statut === "a_venir" && (
            <Button asChild size="sm" variant="outline">
              <Link to={`/etudiant/evaluations/${evaluation.id}`}>
                Voir les détails
              </Link>
            </Button>
          )}
          {evaluation.statut === "terminee" && (
            <Button asChild size="sm" variant="ghost">
              <Link to={`/etudiant/resultats/${evaluation.id}`}>
                Voir mes résultats
              </Link>
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

export default function ListeEvaluations() {
  const [filtre, setFiltre] = useState<"toutes" | StatutEval>("toutes")

  const evaluationsFiltrees =
    filtre === "toutes"
      ? evaluations
      : evaluations.filter((e) => e.statut === filtre)

  return (
    <>
      {/* En-tête */}
      <div>
        <h1 className="text-2xl font-semibold">Mes évaluations</h1>
        <p className="text-sm text-muted-foreground">
          Consultez et démarrez vos évaluations disponibles.
        </p>
      </div>

      {/* Filtres par statut */}
      <Tabs value={filtre} onValueChange={(v) => setFiltre(v as typeof filtre)}>
        <TabsList>
          <TabsTrigger value="toutes">Toutes</TabsTrigger>
          <TabsTrigger value="disponible">Disponibles</TabsTrigger>
          <TabsTrigger value="a_venir">À venir</TabsTrigger>
          <TabsTrigger value="terminee">Terminées</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Liste */}
      <div className="flex flex-col gap-3">
        {evaluationsFiltrees.length > 0 ? (
          evaluationsFiltrees.map((evaluation) => (
            <EvaluationCard key={evaluation.id} evaluation={evaluation} />
          ))
        ) : (
          <Card>
            <CardContent className="py-10 text-center text-sm text-muted-foreground">
              Aucune évaluation dans cette catégorie.
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}