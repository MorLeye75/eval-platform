import { Link } from "react-router-dom"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"

/** Écran affiché pendant le chargement de la copie ou quand la passation est impossible. */
export default function EtatPassation({
  message,
  retour = true,
}: {
  message: string
  retour?: boolean
}) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col items-center gap-4 py-10 text-center">
      <Card className="w-full rounded-2xl border-border/60 shadow-sm">
        <CardContent className="py-10 text-sm text-muted-foreground">
          {message}
        </CardContent>
      </Card>
      {retour && (
        <Button
          variant="outline"
          render={<Link to="/etudiant/evaluations">Retour aux évaluations</Link>}
        />
      )}
    </div>
  )
}
