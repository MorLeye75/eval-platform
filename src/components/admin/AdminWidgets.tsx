import type { LucideIcon } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"

export type Tone = "green" | "amber" | "red" | "blue" | "gray"

export const TONE_CLS: Record<Tone, string> = {
  green: "bg-primary/10 text-primary",
  amber: "bg-yellow-500/10 text-yellow-700 dark:text-yellow-400",
  red:   "bg-destructive/10 text-destructive",
  blue:  "bg-blue-500/10 text-blue-700 dark:text-blue-400",
  gray:  "bg-muted text-muted-foreground",
}

export function ToneBadge({
  tone,
  dot,
  children,
}: {
  tone: Tone
  dot?: boolean
  children: React.ReactNode
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-[3px] text-[11px] font-semibold ${TONE_CLS[tone]}`}
    >
      {dot && <span className="size-1.5 rounded-full bg-current" />}
      {children}
    </span>
  )
}

export function StatCard({
  label,
  value,
  icon: Icon,
  hint,
  dir,
}: {
  label: string
  value: string
  icon: LucideIcon
  hint?: string
  dir?: "up" | "down"
}) {
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
