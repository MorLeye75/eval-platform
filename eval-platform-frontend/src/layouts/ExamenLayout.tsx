import { Outlet } from "react-router-dom"
import { GraduationCap } from "lucide-react"

export function ExamenLayout() {
  return (
    <div className="min-h-svh bg-muted/30">
      {/* Barre supérieure minimale — pas de navigation, pas de sortie facile */}
      <header className="sticky top-0 z-10 border-b border-border/60 bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-4xl items-center gap-3 px-4">
          <div className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <GraduationCap className="size-4" />
          </div>
          <span className="text-sm font-semibold">Évaluation en cours</span>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}