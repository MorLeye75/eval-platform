import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Link } from "react-router-dom"
import { Construction, ArrowLeft } from "lucide-react"

export function FeaturePlaceholder({
  titre,
  description = "Ce module est en cours de développement et sera disponible prochainement.",
  retourUrl = "/enseignant",
  retourTexte = "Retour au tableau de bord",
}: {
  titre: string
  description?: string
  retourUrl?: string
  retourTexte?: string
}) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col items-center gap-6 py-12 text-center">
      <div className="flex size-14 items-center justify-center rounded-2xl bg-amber-500/10 text-amber-600 dark:text-amber-400">
        <Construction className="size-7" />
      </div>

      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold tracking-tight">{titre}</h1>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>

      <Card className="w-full rounded-2xl border-border/60 shadow-sm">
        <CardContent className="py-6 text-sm text-muted-foreground">
          Les équipes finalisent l'intégration de cette fonctionnalité avec le backend.
        </CardContent>
      </Card>

      <Button
        variant="outline"
        className="rounded-xl"
        render={
          <Link to={retourUrl}>
            <ArrowLeft className="mr-1 size-4" />
            {retourTexte}
          </Link>
        }
      />
    </div>
  )
}
