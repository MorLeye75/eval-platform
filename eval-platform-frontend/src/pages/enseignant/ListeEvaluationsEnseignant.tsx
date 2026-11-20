import { useState, type ReactNode } from "react"
import { Link, useNavigate } from "react-router-dom"
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
  Timer,
  Copy,
} from "lucide-react"
import type { StatutEvaluation } from "@/types"
import { useIsMobile } from "@/hooks/use-mobile"
import {
  CLE_EVALUATIONS_ENSEIGNANT,
  actionsDisponibles,
  depublierEvaluation,
  libelleNbQuestions,
  mesEvaluationsEnseignant,
  publierEvaluation,
  publierResultats,
  supprimerEvaluation,
  type EvaluationEnseignantApi,
} from "@/lib/evaluationsEnseignant"
import { messageErreur } from "@/lib/passation"
import { dupliquerEvaluation } from "./creation/apiBrouillon"

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

/** Sur téléphone : cibles assez grandes pour un doigt (40 px). */
const BOUTON_ICONE_TACTILE = BOUTON_ICONE.replace("size-8", "size-10")

/** Actions d'une ligne, déclenchées par la page (mutations et confirmations communes au tableau et aux cartes). */
interface GestesEvaluation {
  dupliquer: (id: number) => void
  publier: (id: number) => void
  depublier: (id: number) => void
  publierResultats: (e: EvaluationEnseignantApi) => void
  supprimer: (e: EvaluationEnseignantApi) => void
}

function StatutEtResultats({ e }: { e: EvaluationEnseignantApi }) {
  return (
    <div className="flex flex-col items-start gap-1">
      <StatutBadge statut={e.statut} />
      {e.statut === "TERMINEE" && (
        <span className="text-[11px] text-muted-foreground">
          {e.resultatsPublies ? "Résultats publiés" : "Résultats non publiés"}
        </span>
      )}
    </div>
  )
}

function Soumissions({ copies, attendus }: { copies: number; attendus: number }) {
  return (
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
  )
}

function ActionsEvaluation({ e, occupe, gestes, className, tactile = false }: {
  e: EvaluationEnseignantApi
  occupe: boolean
  gestes: GestesEvaluation
  className: string
  tactile?: boolean
}) {
  const a = actionsDisponibles(e)
  const bouton = tactile ? BOUTON_ICONE_TACTILE : BOUTON_ICONE
  const icone = tactile ? "size-4" : "size-3.5"
  return (
    <div className={className}>
      {a.statistiques && (
        <Link to={`/enseignant/statistiques/${e.id}`} aria-label={`Statistiques : ${e.titre}`} title="Statistiques"
          className={bouton}>
          <BarChart3 className={icone} aria-hidden />
        </Link>
      )}
      {a.supervision && (
        <Link to={`/enseignant/supervision/${e.id}`} aria-label={`Supervision : ${e.titre}`} title="Supervision"
          className={bouton}>
          <Eye className={icone} aria-hidden />
        </Link>
      )}
      {a.dupliquer && (
        <button type="button" disabled={occupe} onClick={() => gestes.dupliquer(e.id)}
          aria-label={`Dupliquer : ${e.titre}`} title="Dupliquer en nouveau brouillon" className={bouton}>
          <Copy className={icone} aria-hidden />
        </button>
      )}
      {a.tiersTemps && (
        <Link to={`/enseignant/evaluations/${e.id}/tiers-temps`} aria-label={`Tiers-temps : ${e.titre}`}
          title="Tiers-temps" className={bouton}>
          <Timer className={icone} aria-hidden />
        </Link>
      )}
      {a.modifier && (
        <Link to={`/enseignant/evaluations/${e.id}/edition`} state={{ nouveau: true }} aria-label={`Modifier : ${e.titre}`}
          title="Modifier" className={bouton}>
          <Pencil className={icone} aria-hidden />
        </Link>
      )}
      {a.publier && (
        <button type="button" disabled={occupe} onClick={() => gestes.publier(e.id)}
          aria-label={`Publier : ${e.titre}`} title="Publier" className={bouton}>
          <Send className={icone} aria-hidden />
        </button>
      )}
      {a.depublier && (
        <button type="button" disabled={occupe} onClick={() => gestes.depublier(e.id)}
          aria-label={`Repasser en brouillon : ${e.titre}`} title="Repasser en brouillon" className={bouton}>
          <Undo2 className={icone} aria-hidden />
        </button>
      )}
      {a.publierResultats && (
        <button type="button" disabled={occupe} onClick={() => gestes.publierResultats(e)}
          aria-label={`Publier les résultats : ${e.titre}`} title="Publier les résultats" className={bouton}>
          <Megaphone className={icone} aria-hidden />
        </button>
      )}
      {a.supprimer && (
        <button type="button" disabled={occupe} onClick={() => gestes.supprimer(e)}
          aria-label={`Supprimer : ${e.titre}`} title="Supprimer"
          className={`${bouton} hover:bg-destructive/10 hover:text-destructive`}>
          <Trash2 className={icone} aria-hidden />
        </button>
      )}
    </div>
  )
}

/** Une évaluation sur téléphone : les informations du tableau, empilées, et ses actions en bas. */
function CarteEvaluation({ e, occupe, gestes }: { e: EvaluationEnseignantApi; occupe: boolean; gestes: GestesEvaluation }) {
  return (
    <article aria-label={e.titre}>
    <Card className="rounded-2xl border-border/60 shadow-sm">
      <CardContent className="flex flex-col gap-3 p-4">
        <div className="flex items-start gap-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-muted text-primary">
            <FileText className="size-[18px]" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="font-semibold break-words">{e.titre}</div>
            <div className="text-[11px] text-muted-foreground">{e.dureeStandard} min</div>
          </div>
          <StatutEtResultats e={e} />
        </div>
        <dl className="grid grid-cols-2 gap-x-3 gap-y-2 text-sm">
          <div>
            <dt className="text-[11px] text-muted-foreground">Questions</dt>
            <dd>{libelleNbQuestions(e.nbQuestions)}</dd>
          </div>
          <div>
            <dt className="text-[11px] text-muted-foreground">Fermeture</dt>
            <dd>{formatDate(e.dateFermeture)}</dd>
          </div>
          <div className="col-span-2">
            <dt className="text-[11px] text-muted-foreground">Soumissions</dt>
            <dd><Soumissions copies={e.nbCopies ?? 0} attendus={e.nbAttendus ?? 0} /></dd>
          </div>
        </dl>
        <ActionsEvaluation e={e} occupe={occupe} gestes={gestes} tactile
          className="flex flex-wrap gap-2 border-t border-border/60 pt-3" />
      </CardContent>
    </Card>
    </article>
  )
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
  const [tab, setTab]       = useState<string>("toutes")
  const [query, setQuery]   = useState("")
  const [erreur, setErreur] = useState<string | null>(null)
  const [info, setInfo]     = useState<string | null>(null)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const estTelephone = useIsMobile()

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
  // La copie est un nouveau brouillon : on ouvre directement l'assistant pour en corriger les dates
  const dupliquer = useMutation({
    ...action(dupliquerEvaluation, (c) => `Copie créée : « ${c.titre} ».`, "La duplication a échoué."),
    onSuccess: (c) => navigate(`/enseignant/evaluations/${c.id}/edition`, { state: { nouveau: true } }),
  })
  const occupe = publier.isPending || depublier.isPending || supprimer.isPending || resultats.isPending || dupliquer.isPending

  const toutes = evaluations.data ?? []
  const filtered = toutes.filter((e) => {
    const matchTab   = tab === "toutes" || e.statut === tab
    const matchQuery = e.titre.toLowerCase().includes(query.toLowerCase())
    return matchTab && matchQuery
  })

  // Confirmations et mutations communes au tableau et aux cartes
  const gestes: GestesEvaluation = {
    dupliquer: (id) => dupliquer.mutate(id),
    publier: (id) => publier.mutate(id),
    depublier: (id) => depublier.mutate(id),
    publierResultats: (e) => {
      if (window.confirm(`Publier les résultats de « ${e.titre} » ? Les étudiants verront leurs notes et les corrigés.`)) {
        resultats.mutate(e.id)
      }
    },
    supprimer: (e) => {
      if (window.confirm(`Supprimer le brouillon « ${e.titre} » ? Cette action est définitive.`)) {
        supprimer.mutate(e.id)
      }
    },
  }

  // Chargement, erreur ou liste vide : même message dans le tableau et sur téléphone
  const etat = evaluations.isPending
    ? { role: "status", erreur: false, contenu: "Chargement des évaluations…" as ReactNode }
    : evaluations.isError
      ? {
          role: "alert", erreur: true,
          contenu: <>Impossible de charger vos évaluations.{" "}
            <button type="button" onClick={() => evaluations.refetch()} className="underline">Réessayer</button></>,
        }
      : filtered.length === 0
        ? { role: undefined, erreur: false,
            contenu: toutes.length === 0 ? "Vous n'avez pas encore créé d'évaluation." : "Aucune évaluation trouvée." }
        : null

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">

      {/* En-tête */}
      <div className="flex flex-wrap items-start justify-between gap-4">
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

      {/* Liste : tableau sur ordinateur, une carte par évaluation sur téléphone (mêmes données, mêmes actions) */}
      {estTelephone ? (
        etat ? (
          <Card className="rounded-2xl border-border/60 shadow-sm">
            <CardContent role={etat.role} className={`py-10 text-center text-sm ${etat.erreur ? "text-destructive" : "text-muted-foreground"}`}>
              {etat.contenu}
            </CardContent>
          </Card>
        ) : (
          <div className="flex flex-col gap-3">
            {filtered.map((e) => (
              <CarteEvaluation key={e.id} e={e} occupe={occupe} gestes={gestes} />
            ))}
          </div>
        )
      ) : (
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
                {etat ? (
                  <TableRow>
                    <TableCell colSpan={6} role={etat.role}
                      className={`py-12 text-center ${etat.erreur ? "text-destructive" : "text-muted-foreground"}`}>
                      {etat.contenu}
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
                        <StatutEtResultats e={e} />
                      </TableCell>
                      {/* Soumissions */}
                      <TableCell>
                        <Soumissions copies={e.nbCopies ?? 0} attendus={e.nbAttendus ?? 0} />
                      </TableCell>
                      {/* Date fermeture */}
                      <TableCell className="text-sm text-muted-foreground">
                        {formatDate(e.dateFermeture)}
                      </TableCell>
                      {/* Actions */}
                      <TableCell className="text-right">
                        <ActionsEvaluation e={e} occupe={occupe} gestes={gestes} className="flex justify-end gap-1.5" />
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {evaluations.isSuccess && (
        <p className="text-xs text-muted-foreground">
          {filtered.length} sur {toutes.length} évaluation{toutes.length > 1 ? "s" : ""}
        </p>
      )}
    </div>
  )
}
