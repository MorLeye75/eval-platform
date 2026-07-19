import { Link } from "react-router-dom"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Clock, Home, ListChecks, Lock } from "lucide-react"

export default function ExpirationScreen() {
  // Données mockées — à remplacer par la réponse du serveur
  const recapitulatif = {
    titre: "Structures de données — QCM",
    questionsRepondues: 2,
    questionsTotal: 3,
  }

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-6 py-8 text-center">
      {/* Icône d'expiration */}
      <div className="flex size-16 items-center justify-center rounded-full bg-destructive/10">
        <Clock className="size-8 text-destructive" />
      </div>

      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-extrabold tracking-tight">
          Temps écoulé
        </h1>
        <p className="text-muted-foreground">
          La durée de l'évaluation est terminée. Votre copie a été soumise
          automatiquement dans l'état de la dernière sauvegarde.
        </p>
      </div>

      {/* Récapitulatif */}
      <Card className="w-full rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-col gap-3 py-5 text-left text-sm">
          <div className="flex items-center justify-between gap-4">
            <span className="text-muted-foreground">Évaluation</span>
            <span className="font-semibold">{recapitulatif.titre}</span>
          </div>
          <div className="flex items-center justify-between gap-4">
            <span className="text-muted-foreground">Questions traitées</span>
            <span className="font-semibold">
              {recapitulatif.questionsRepondues} / {recapitulatif.questionsTotal}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Message de verrouillage */}
      <div className="flex w-full items-start gap-3 rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-left text-sm text-destructive">
        <Lock className="mt-0.5 size-4 shrink-0" />
        <span>
          L'accès à cette évaluation est désormais verrouillé. Vous ne pouvez
          plus modifier vos réponses.
        </span>
      </div>

      {/* Actions */}
      <div className="flex w-full flex-col gap-2 sm:flex-row sm:justify-center">
        <Button
          variant="outline"
          className="w-full sm:w-auto"
          render={
            <Link to="/etudiant/evaluations">
              <ListChecks className="mr-1 size-4" />
              Mes évaluations
            </Link>
          }
        />
        <Button
          className="w-full shadow-md sm:w-auto"
          render={
            <Link to="/etudiant/dashboard">
              <Home className="mr-1 size-4" />
              Tableau de bord
            </Link>
          }
        />
      </div>
    </div>
  )
}