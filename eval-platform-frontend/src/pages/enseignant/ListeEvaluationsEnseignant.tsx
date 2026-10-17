import { useState } from "react"
import { Link } from "react-router-dom"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Plus,
  Search,
  Eye,
  BarChart3,
  Pencil,
  Trash2,
  Copy,
  FileText,
} from "lucide-react"
import type { StatutEvaluation } from "@/types"
import { evaluationsEnseignantMock as MOCK_EVALS } from "@/mocks/evaluationsEnseignant"

// ──────────────────────────────────────────────────────────
//  Config statuts
// ──────────────────────────────────────────────────────────

const STATUT_CONFIG: Record<StatutEvaluation, { label: string; cls: string }> = {
  BROUILLON: { label: "Brouillon", cls: "bg-muted text-muted-foreground border-transparent"                          },
  PUBLIEE:   { label: "Publiée",   cls: "bg-primary/10 text-primary border-primary/20"                              },
  EN_COURS:  { label: "En cours",  cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"        },
  TERMINEE:  { label: "Terminée",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400"},
}

function StatutBadge({ statut }: { statut: StatutEvaluation }) {
  const { label, cls } = STATUT_CONFIG[statut]
  return (
    <Badge variant="outline" className={`rounded-full px-3 py-1 text-xs font-semibold ${cls}`}>
      {label}
    </Badge>
  )
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("fr-FR", {
    day: "numeric", month: "short", year: "numeric",
  })
}

// ──────────────────────────────────────────────────────────
//  Composant principal
// ──────────────────────────────────────────────────────────

const TABS = [
  { value: "toutes",    label: "Toutes"    },
  { value: "BROUILLON", label: "Brouillons"},
  { value: "EN_COURS",  label: "En cours"  },
  { value: "TERMINEE",  label: "Terminées" },
  { value: "PUBLIEE",   label: "Publiées"  },
] as const

export default function ListeEvaluationsEnseignant() {
  const [tab, setTab]     = useState<string>("toutes")
  const [query, setQuery] = useState("")

  const filtered = MOCK_EVALS.filter((e) => {
    const matchTab   = tab === "toutes" || e.statut === tab
    const matchQuery = e.titre.toLowerCase().includes(query.toLowerCase())
    return matchTab && matchQuery
  })

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">

      {/* En-tête */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Mes évaluations</h1>
          <p className="mt-1 text-muted-foreground">
            Créez, gérez et suivez toutes vos évaluations.
          </p>
        </div>
        <Button
          className="rounded-xl"
          render={
            <Link to="/enseignant/evaluations/creation" state={{ nouveau: true }}>
              <Plus className="size-4" /> Nouvelle évaluation
            </Link>
          }
        />
      </div>

      {/* Barre outils */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher une évaluation..."
            className="rounded-xl pl-9"
          />
        </div>
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList className="rounded-xl">
            {TABS.map((t) => (
              <TabsTrigger key={t.value} value={t.value}>
                {t.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {/* Table */}
      <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Évaluation</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Statut</TableHead>
                <TableHead>Soumissions</TableHead>
                <TableHead>Fermeture</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-12 text-center text-muted-foreground">
                    Aucune évaluation trouvée.
                  </TableCell>
                </TableRow>
              ) : (
                filtered.map((e) => (
                  <TableRow key={e.id}>
                    {/* Titre */}
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
                          <FileText className="size-[18px]" />
                        </span>
                        <div>
                          <div className="font-semibold">{e.titre}</div>
                          <div className="text-[11px] text-muted-foreground">
                            {e.duree} min
                          </div>
                        </div>
                      </div>
                    </TableCell>
                    {/* Type */}
                    <TableCell className="text-sm text-muted-foreground">
                      {e.typeLabel}
                    </TableCell>
                    {/* Statut */}
                    <TableCell>
                      <StatutBadge statut={e.statut} />
                    </TableCell>
                    {/* Soumissions */}
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold">
                          {e.nbSoumissions}
                        </span>
                        <span className="text-[11px] text-muted-foreground">
                          / {e.nbAttendus}
                        </span>
                        {/* Barre de progression mini */}
                        {e.nbAttendus > 0 && (
                          <div className="h-1.5 w-16 overflow-hidden rounded-full bg-muted">
                            <div
                              className="h-full rounded-full bg-primary"
                              style={{ width: `${Math.round((e.nbSoumissions / e.nbAttendus) * 100)}%` }}
                            />
                          </div>
                        )}
                      </div>
                    </TableCell>
                    {/* Date fermeture */}
                    <TableCell className="text-sm text-muted-foreground">
                      {formatDate(e.dateFermeture)}
                    </TableCell>
                    {/* Actions */}
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1.5">
                        {/* Voir stats — disponible si terminée ou publiée */}
                        {(e.statut === "TERMINEE" || e.statut === "PUBLIEE") && (
                          <Link to={`/enseignant/statistiques/${e.id}`}>
                            <button className="inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
                              <BarChart3 className="size-3.5" />
                            </button>
                          </Link>
                        )}
                        {/* Supervision — disponible si en cours */}
                        {e.statut === "EN_COURS" && (
                          <Link to={`/enseignant/supervision/${e.id}`}>
                            <button className="inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
                              <Eye className="size-3.5" />
                            </button>
                          </Link>
                        )}
                        {/* Modifier — disponible si brouillon */}
                        {e.statut === "BROUILLON" && (
                          <Link to={`/enseignant/evaluations/${e.id}/edition`} state={{ nouveau: true }}>
                            <button className="inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
                              <Pencil className="size-3.5" />
                            </button>
                          </Link>
                        )}
                        {/* Dupliquer — toujours disponible */}
                        <button className="inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
                          <Copy className="size-3.5" />
                        </button>
                        {/* Supprimer — disponible si brouillon seulement */}
                        {e.statut === "BROUILLON" && (
                          <button className="inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive">
                            <Trash2 className="size-3.5" />
                          </button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Pagination */}
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>{filtered.length} sur {MOCK_EVALS.length} évaluations</span>
        <div className="flex gap-1.5">
          <button className="rounded-lg border border-border bg-background px-3 py-1.5 font-semibold transition-colors hover:bg-muted">
            ← Précédent
          </button>
          <button className="rounded-lg border border-border bg-background px-3 py-1.5 font-semibold transition-colors hover:bg-muted">
            Suivant →
          </button>
        </div>
      </div>
    </div>
  )
}
