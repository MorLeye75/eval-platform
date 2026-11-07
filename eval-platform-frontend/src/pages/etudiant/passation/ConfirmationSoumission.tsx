import { Link, useLocation } from "react-router-dom"
import { formatHeure } from "@/lib/passation"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { CircleCheck, Home, ListChecks } from "lucide-react"

interface EtatConfirmation {
  titre: string
  traitees?: number // absent pour un quiz chronométré
  total?: number
  dateRemise?: string | null
}

export default function ConfirmationSoumission() {
  const location = useLocation()
  const etat = location.state as EtatConfirmation | null

  // Heure enregistrée par le serveur (celle qui fait foi), à défaut l'heure actuelle
  const heureSoumission = formatHeure(etat?.dateRemise ?? undefined)

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-6 py-8 text-center">
      {/* Icône de succès */}
      <div className="flex size-16 items-center justify-center rounded-full bg-primary/10">
        <CircleCheck className="size-8 text-primary" />
      </div>

      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-extrabold tracking-tight">
          Évaluation soumise
        </h1>
        <p className="text-muted-foreground">
          Votre copie a bien été enregistrée. Vous serez notifié dès que les
          résultats seront publiés par votre enseignant.
        </p>
      </div>

      {/* Récapitulatif */}
      <Card className="w-full rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-col gap-3 py-5 text-left text-sm">
          {etat && (
            <>
              <div className="flex items-center justify-between gap-4">
                <span className="text-muted-foreground">Évaluation</span>
                <span className="font-semibold">{etat.titre}</span>
              </div>
              {etat.traitees !== undefined && (
                <div className="flex items-center justify-between gap-4">
                  <span className="text-muted-foreground">Questions traitées</span>
                  <span className="font-semibold">
                    {etat.traitees} / {etat.total}
                  </span>
                </div>
              )}
            </>
          )}
          <div className="flex items-center justify-between gap-4">
            <span className="text-muted-foreground">Heure de soumission</span>
            <span className="font-semibold">{heureSoumission}</span>
          </div>
        </CardContent>
      </Card>

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
