// src/components/enseignant/StatutBadge.tsx
// Composant partagé — extrait de DashboardEnseignant et ListeEvaluationsEnseignant
// Usage : import { StatutBadge, STATUT_CONFIG } from "@/components/enseignant/StatutBadge"

import { Badge } from "@/components/ui/badge"
import type { StatutEvaluation } from "@/types"

export const STATUT_CONFIG: Record<StatutEvaluation, { label: string; cls: string }> = {
  BROUILLON: { label: "Brouillon", cls: "bg-muted text-muted-foreground border-transparent"                           },
  PUBLIEE:   { label: "Publiée",   cls: "bg-primary/10 text-primary border-primary/20"                               },
  EN_COURS:  { label: "En cours",  cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"         },
  TERMINEE:  { label: "Terminée",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400" },
}

export function StatutBadge({ statut }: { statut: StatutEvaluation }) {
  const { label, cls } = STATUT_CONFIG[statut]
  return (
    <Badge variant="outline" className={`rounded-full px-3 py-1 text-xs font-semibold ${cls}`}>
      {label}
    </Badge>
  )
}
