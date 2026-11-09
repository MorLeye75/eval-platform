import { useState } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
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
  FileText,
  Send,
  Undo2,
  Megaphone,
} from "lucide-react"
import type { StatutEvaluation } from "@/types"
import {
  CLE_EVALUATIONS_ENSEIGNANT,
  actionsDisponibles,
  depublierEvaluation,
  libelleNbQuestions,
  mesEvaluationsEnseignant,
  publierEvaluation,
  publierResultats,
  supprimerEvaluation,
} from "@/lib/evaluationsEnseignant"
import { messageErreur } from "@/lib/passation"

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

const BOUTON_ICONE =
  "inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"

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
  const [tab, setTab]       = useState<string>("toutes")
  const [query, setQuery]   = useState("")
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo]     = useState<string | null>(null)
  const queryClient = useQueryClient()

  const evaluations = useQuery({
    queryKey: CLE_EVALUATIONS_ENSEIGNANT,
    queryFn: mesEvaluationsEnseignant,
  })

  // Une action de la ligne : message de succès ou d'erreur du serveur, liste rechargée dans les deux cas
  function action<T>(fn: (id: number) => Promise<T>, succes: (resultat: T) => string, echec: string) {
    return {
      mutationFn: fn,
      onSuccess: (resultat: T) => { setErreur(null); setInfo(succes(resultat)) },
      onError: (e: unknown) => { setInfo(null); setErreur(messageErreur(e, echec)) },
      onSettled: () => queryClient.invalidateQueries({ queryKey: CLE_EVALUATIONS_ENSEIGNANT }),
    }
  }
  const publier = useMutation(action(publierEvaluation, () => "Évaluation publiée.", "La publication a échoué."))
  const depublier = useMutation(action(depublierEvaluation, () => "Évaluation repassée en brouillon.", "L'opération a échoué."))
  const supprimer = useMutation(action(supprimerEvaluation, () => "Brouillon supprimé.", "La suppression a échoué."))
  const resultats = useMutation(action(
    publierResultats,
    (r) => `Résultats publiés : ${r.nbCopiesNotees} copie${r.nbCopiesNotees > 1 ? "s" : ""} notifiée${r.nbCopiesNotees > 1 ? "s" : ""}`
      + (r.nbCopiesEnAttente > 0 ? `, ${r.nbCopiesEnAttente} en attente de notation.` : "."),
    "La publication des résultats a échoué.",
  ))
  const occupe = publier.isPending || depublier.isPending || supprimer.isPending || resultats.isPending

  const toutes = evaluations.data ?? []
  const filtered = toutes.filter((e) => {
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

      {erreur && (
        <p role="alert" className="rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {erreur}
        </p>
      )}
      {info && (
        <p role="status" className="rounded-xl border border-primary/20 bg-primary/5 px-4 py-2.5 text-sm text-foreground">
          {info}
        </p>
      )}

      {/* Barre outils */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher une évaluation..."
            aria-label="Rechercher une évaluation"
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
                <TableHead>Questions</TableHead>
                <TableHead>Statut</TableHead>
                <TableHead>Soumissions</TableHead>
                <TableHead>Fermeture</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {evaluations.isPending ? (
                <TableRow>
                  <TableCell colSpan={6} role="status" className="py-12 text-center text-muted-foreground">
                    Chargement des évaluations…
                  </TableCell>
                </TableRow>
              ) : evaluations.isError ? (
                <TableRow>
                  <TableCell colSpan={6} role="alert" className="py-12 text-center text-destructive">
                    Impossible de charger vos évaluations.{" "}
                    <button type="button" onClick={() => evaluations.refetch()} className="underline">Réessayer</button>
                  </TableCell>
                </TableRow>
              ) : filtered.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-12 text-center text-muted-foreground">
                    {toutes.length === 0 ? "Vous n'avez pas encore créé d'évaluation." : "Aucune évaluation trouvée."}
                  </TableCell>
                </TableRow>
              ) : (
                filtered.map((e) => {
                  const a = actionsDisponibles(e)
                  const copies = e.nbCopies ?? 0
                  const attendus = e.nbAttendus ?? 0
                  return (
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
                              {e.dureeStandard} min
                            </div>
                          </div>
                        </div>
                      </TableCell>
                      {/* Questions */}
                      <TableCell className="text-sm text-muted-foreground">
                        {libelleNbQuestions(e.nbQuestions)}
                      </TableCell>
                      {/* Statut */}
                      <TableCell>
                        <div className="flex flex-col items-start gap-1">
                          <StatutBadge statut={e.statut} />
                          {e.statut === "TERMINEE" && (
                            <span className="text-[11px] text-muted-foreground">
                              {e.resultatsPublies ? "Résultats publiés" : "Résultats non publiés"}
                            </span>
                          )}
                        </div>
                      </TableCell>
                      {/* Soumissions */}
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-semibold">{copies}</span>
                          <span className="text-[11px] text-muted-foreground">/ {attendus}</span>
                          {attendus > 0 && (
                            <div className="h-1.5 w-16 overflow-hidden rounded-full bg-muted">
                              <div
                                className="h-full rounded-full bg-primary"
                                style={{ width: `${Math.min(100, Math.round((copies / attendus) * 100))}%` }}
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
                          {a.statistiques && (
                            <Link
                              to={`/enseignant/statistiques/${e.id}`}
                              aria-label={`Statistiques : ${e.titre}`}
                              title="Statistiques"
                              className={BOUTON_ICONE}
                            >
                              <BarChart3 className="size-3.5" aria-hidden />
                            </Link>
                          )}
                          {a.supervision && (
                            <Link
                              to={`/enseignant/supervision/${e.id}`}
                              aria-label={`Supervision : ${e.titre}`}
                              title="Supervision"
                              className={BOUTON_ICONE}
                            >
                              <Eye className="size-3.5" aria-hidden />
                            </Link>
                          )}
                          {a.modifier && (
                            <Link
                              to={`/enseignant/evaluations/${e.id}/edition`}
                              state={{ nouveau: true }}
                              aria-label={`Modifier : ${e.titre}`}
                              title="Modifier"
                              className={BOUTON_ICONE}
                            >
                              <Pencil className="size-3.5" aria-hidden />
                            </Link>
                          )}
                          {a.publier && (
                            <button
                              type="button"
                              disabled={occupe}
                              onClick={() => publier.mutate(e.id)}
                              aria-label={`Publier : ${e.titre}`}
                              title="Publier"
                              className={BOUTON_ICONE}
                            >
                              <Send className="size-3.5" aria-hidden />
                            </button>
                          )}
                          {a.depublier && (
                            <button
                              type="button"
                              disabled={occupe}
                              onClick={() => depublier.mutate(e.id)}
                              aria-label={`Repasser en brouillon : ${e.titre}`}
                              title="Repasser en brouillon"
                              className={BOUTON_ICONE}
                            >
                              <Undo2 className="size-3.5" aria-hidden />
                            </button>
                          )}
                          {a.publierResultats && (
                            <button
                              type="button"
                              disabled={occupe}
                              onClick={() => {
                                if (window.confirm(`Publier les résultats de « ${e.titre} » ? Les étudiants verront leurs notes et les corrigés.`)) {
                                  resultats.mutate(e.id)
                                }
                              }}
                              aria-label={`Publier les résultats : ${e.titre}`}
                              title="Publier les résultats"
                              className={BOUTON_ICONE}
                            >
                              <Megaphone className="size-3.5" aria-hidden />
                            </button>
                          )}
                          {a.supprimer && (
                            <button
                              type="button"
                              disabled={occupe}
                              onClick={() => {
                                if (window.confirm(`Supprimer le brouillon « ${e.titre} » ? Cette action est définitive.`)) {
                                  supprimer.mutate(e.id)
                                }
                              }}
                              aria-label={`Supprimer : ${e.titre}`}
                              title="Supprimer"
                              className={`${BOUTON_ICONE} hover:bg-destructive/10 hover:text-destructive`}
                            >
                              <Trash2 className="size-3.5" aria-hidden />
                            </button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {evaluations.isSuccess && (
        <p className="text-xs text-muted-foreground">
          {filtered.length} sur {toutes.length} évaluation{toutes.length > 1 ? "s" : ""}
        </p>
      )}
    </div>
  )
}
