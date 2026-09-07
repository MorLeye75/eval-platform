// src/components/admin/StatCard.tsx
// Composant partagé extrait de DashboardAdmin, SupervisionGlobale, StatistiquesGlobales
// Usage : import { StatCard } from "@/components/admin/StatCard"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import type { LucideIcon } from "lucide-react"

interface StatCardProps {
  label: string
  value: string
  icon: LucideIcon
  hint?: string
  dir?: "up" | "down"
}

export function StatCard({ label, value, icon: Icon, hint, dir }: StatCardProps) {
  const hintCls =
    dir === "up"
      ? "text-primary"
      : dir === "down"
      ? "text-destructive"
      : "text-muted-foreground"

  return (
    <Card className="rounded-2xl border-border/60 shadow-sm">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">
          {label}
        </CardTitle>
        <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <Icon className="size-4" />
        </span>
      </CardHeader>
      <CardContent>
        <div className="text-3xl font-extrabold tracking-tight">{value}</div>
        {hint && <p className={`mt-0.5 text-[11px] ${hintCls}`}>{hint}</p>}
      </CardContent>
    </Card>
  )
}
