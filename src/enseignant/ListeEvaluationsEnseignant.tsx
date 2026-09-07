import { useState } from "react"
import { Link } from "react-router-dom"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Plus, Search, Eye, BarChart3, Pencil, Trash2, Copy, FileText } from "lucide-react"
import { StatutBadge } from "@/components/enseignant/StatutBadge"
import type { Evaluation } from "@/types"

// ============================================================
// Données mockées — à remplacer par :
// const { data } = useQuery({ queryKey: ["enseignant-evaluations"], queryFn: () => api.get("/enseignant/evaluations") })
// ============================================================

interface EvaluationListe extends Evaluation {
  nbSoumissions: number
  nbAttendus: number
  typeLabel: string
}

const MOCK_EVALS: EvaluationListe[] = [
  { id: 1, titre: "Structures de données — QCM",         description: "", statut: "EN_COURS",  duree: 45, dateOuverture: "2026-07-10T08:00:00", dateFermeture: "2026-07-12T23:59:00", resultatsPublies: false, nbSoumissions: 32, nbAttendus: 45, typeLabel: "QCM"              },
  { id: 2, titre: "Algorithmique — exercice de code",    description: "", statut: "BROUILLON", duree: 90, dateOuverture: "2026-07-15T08:00:00", dateFermeture: "2026-07-15T23:59:00", resultatsPublies: false, nbSoumissions: 0,  nbAttendus: 45, typeLabel: "Code"             },
  { id: 3, titre: "Réseaux — questions ouvertes",        description: "", statut: "TERMINEE",  duree: 60, dateOuverture: "2026-07-01T08:00:00", dateFermeture: "2026-07-05T23:59:00", resultatsPublies: true,  nbSoumissions: 38, nbAttendus: 38, typeLabel: "Question ouverte" },
  { id: 4, titre: "Bases de données — quiz chronométré", description: "", statut: "PUBLIEE",   duree: 30, dateOuverture: "2026-07-08T08:00:00", dateFermeture: "2026-07-10T18:00:00", resultatsPublies: false, nbSoumissions: 40, nbAttendus: 45, typeLabel: "Quiz"             },
  { id: 5, titre: "Génie logiciel — questions ouvertes", description: "", statut: "BROUILLON", duree: 60, dateOuverture: "2026-07-20T08:00:00", dateFermeture: "2026-07-20T23:59:00", resultatsPublies: false, nbSoumissions: 0,  nbAttendus: 45, typeLabel: "Question ouverte" },
  { id: 6, titre: "Systèmes d'exploitation — QCM",      description: "", statut: "TERMINEE",  duree: 45, dateOuverture: "2026-06-20T08:00:00", dateFermeture: "2026-06-20T23:59:00", resultatsPublies: true,  nbSoumissions: 42, nbAttendus: 42, typeLabel: "QCM"              },
]

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" })
}

const TABS = [
  { value: "toutes",     label: "Toutes"     },
  { value: "BROUILLON",  label: "Brouillons" },
  { value: "EN_COURS",   label: "En cours"   },
  { value: "TERMINEE",   label: "Terminées"  },
  { value: "PUBLIEE",    label: "Publiées"   },
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

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Mes évaluations</h1>
          <p className="mt-1 text-muted-foreground">Créez, gérez et suivez toutes vos évaluations.</p>
        </div>
        {/* ✅ Button asChild — cohérent avec DashboardEnseignant */}
        <Button asChild className="rounded-xl">
          <Link to="/enseignant/evaluations/creation">
            <Plus className="size-4" /> Nouvelle évaluation
          </Link>
        </Button>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Rechercher une évaluation..." className="rounded-xl pl-9" />
        </div>
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList className="rounded-xl">
            {TABS.map((t) => <TabsTrigger key={t.value} value={t.value}>{t.label}</TabsTrigger>)}
          </TabsList>
        </Tabs>
      </div>

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
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
                          <FileText className="size-[18px]" />
                        </span>
                        <div>
                          <div className="font-semibold">{e.titre}</div>
                          <div className="text-[11px] text-muted-foreground">{e.duree} min</div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{e.typeLabel}</TableCell>
                    <TableCell>
                      {/* ✅ StatutBadge partagé */}
                      <StatutBadge statut={e.statut} />
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold">{e.nbSoumissions}</span>
                        <span className="text-[11px] text-muted-foreground">/ {e.nbAttendus}</span>
                        {e.nbAttendus > 0 && (
                          <div className="h-1.5 w-16 overflow-hidden rounded-full bg-muted">
                            <div className="h-full rounded-full bg-primary" style={{ width: `${Math.round((e.nbSoumissions / e.nbAttendus) * 100)}%` }} />
                          </div>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{formatDate(e.dateFermeture)}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1.5">
                        {/* ✅ Button shadcn variant="ghost" size="icon" pour les actions */}
                        {(e.statut === "TERMINEE" || e.statut === "PUBLIEE") && (
                          <Button variant="ghost" size="icon" className="size-8 rounded-lg border border-border" asChild>
                            <Link to={`/enseignant/statistiques/${e.id}`}><BarChart3 className="size-3.5" /></Link>
                          </Button>
                        )}
                        {e.statut === "EN_COURS" && (
                          <Button variant="ghost" size="icon" className="size-8 rounded-lg border border-border" asChild>
                            <Link to={`/enseignant/supervision/${e.id}`}><Eye className="size-3.5" /></Link>
                          </Button>
                        )}
                        {e.statut === "BROUILLON" && (
                          <Button variant="ghost" size="icon" className="size-8 rounded-lg border border-border" asChild>
                            <Link to={`/enseignant/evaluations/${e.id}/edition`}><Pencil className="size-3.5" /></Link>
                          </Button>
                        )}
                        <Button variant="ghost" size="icon" className="size-8 rounded-lg border border-border">
                          <Copy className="size-3.5" />
                        </Button>
                        {e.statut === "BROUILLON" && (
                          <Button variant="ghost" size="icon" className="size-8 rounded-lg border border-border text-muted-foreground hover:bg-destructive/10 hover:text-destructive">
                            <Trash2 className="size-3.5" />
                          </Button>
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

      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>{filtered.length} sur {MOCK_EVALS.length} évaluations</span>
        <div className="flex gap-1.5">
          <Button variant="outline" size="sm" className="rounded-lg">← Précédent</Button>
          <Button variant="outline" size="sm" className="rounded-lg">Suivant →</Button>
        </div>
      </div>
    </div>
  )
}
