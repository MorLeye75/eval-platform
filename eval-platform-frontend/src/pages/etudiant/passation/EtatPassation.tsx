import type { ReactNode } from "react"
import { Link } from "react-router-dom"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"

/** Écran affiché pendant le chargement de la copie ou quand la passation est impossible. */
export default function EtatPassation({
  message,
  retour = true,
  erreur = false,
  children,
}: {
  message: string
  retour?: boolean
  /** Message d'erreur : affiché en rouge et annoncé aux lecteurs d'écran. */
  erreur?: boolean
  /** Action proposée sous le message (ex. réessayer). */
  children?: ReactNode
}) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col items-center gap-4 py-10 text-center">
      <Card className="w-full rounded-2xl border-border/60 shadow-sm">
        <CardContent
          role={erreur ? "alert" : undefined}
          className={`py-10 text-sm ${erreur ? "text-destructive" : "text-muted-foreground"}`}
        >
          {message}
        </CardContent>
      </Card>
      {children}
      {retour && (
        <Button
          variant="outline"
          render={<Link to="/etudiant/evaluations">Retour aux évaluations</Link>}
        />
      )}
    </div>
  )
}
