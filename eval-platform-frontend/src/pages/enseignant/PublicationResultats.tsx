import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Send, Users, FileText, CheckCircle2, CalendarClock } from "lucide-react"
import { Input } from "@/components/ui/input"
import {
  CLE_EVALUATIONS_ENSEIGNANT,
  libelleNbQuestions,
  annulerProgrammationPublication,
  mesEvaluationsEnseignant,
  programmerPublication,
  publierResultats,
  type ResultatsPublicationApi,
} from "@/lib/evaluationsEnseignant"
import { messageErreur } from "@/lib/passation"
import NotesParEtudiant from "./NotesParEtudiant"

// ============================================================
// Publication des résultats (DS-07). Les évaluations terminées viennent de l'API ; la publication appelle
// POST /api/enseignant/evaluations/{id}/resultats/publier. Les notes par étudiant viennent du serveur (DS-09, section C)
// et s'affichent avant la publication : l'enseignant les relit avant de les rendre visibles.
// ============================================================

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" })
}

function formatDateHeure(iso: string) {
  return new Date(iso).toLocaleString("fr-FR", { day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" })
}

function bilan(r: ResultatsPublicationApi) {
  const notifiees = `${r.nbCopiesNotees} copie${r.nbCopiesNotees > 1 ? "s" : ""} notifiée${r.nbCopiesNotees > 1 ? "s" : ""}`
  return r.nbCopiesEnAttente > 0
    ? `Résultats publiés : ${notifiees}, ${r.nbCopiesEnAttente} en attente de notation (leur étudiant sera notifié à la notation).`
    : `Résultats publiés : ${notifiees}.`
}

export default function PublicationResultats() {
  const [selected, setSelected] = useState<number | null>(null)
  const [tab, setTab]           = useState("a_publier")
  const [erreur, setErreur]     = useState<string | null>(null)
  const [info, setInfo]         = useState<string | null>(null)
  const queryClient = useQueryClient()

  const evaluations = useQuery({ queryKey: CLE_EVALUATIONS_ENSEIGNANT, queryFn: mesEvaluationsEnseignant })

  const publication = useMutation({
    mutationFn: publierResultats,
    onSuccess: (r) => { setErreur(null); setInfo(bilan(r)); setSelected(null) },
    onError: (e) => { setInfo(null); setErreur(messageErreur(e, "La publication des résultats a échoué.")) },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLE_EVALUATIONS_ENSEIGNANT }),
  })

  const [datePrevue, setDatePrevue] = useState("")
  const programmation = useMutation({
    mutationFn: ({ id, date }: { id: number; date: string }) => programmerPublication(id, date),
    onSuccess: (r) => { setErreur(null); setInfo(`Publication programmée le ${formatDateHeure(r.publicationPrevueLe)}.`); setDatePrevue("") },
    onError: (e) => { setInfo(null); setErreur(messageErreur(e, "La programmation a échoué.")) },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLE_EVALUATIONS_ENSEIGNANT }),
  })
  const annulation = useMutation({
    mutationFn: annulerProgrammationPublication,
    onSuccess: () => { setErreur(null); setInfo("Programmation annulée : les résultats ne seront pas publiés automatiquement.") },
    onError: (e) => { setInfo(null); setErreur(messageErreur(e, "L'annulation a échoué.")) },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLE_EVALUATIONS_ENSEIGNANT }),
  })

  // Seules les évaluations terminées ont des résultats à publier
  const terminees = (evaluations.data ?? []).filter((e) => e.statut === "TERMINEE")
  const filtered = terminees.filter((e) => (tab === "a_publier" ? !e.resultatsPublies : e.resultatsPublies))
  const evalSelected = terminees.find((e) => e.id === selected)

  function publier(id: number, titre: string) {
    if (window.confirm(`Publier les résultats de « ${titre} » ? Les étudiants verront leurs notes et les corrigés.`)) {
      publication.mutate(id)
    }
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">

      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Publication des résultats</h1>
        <p className="mt-1 text-muted-foreground">
          Publiez les résultats d'une évaluation terminée : les étudiants voient leur note et les corrigés, et reçoivent une notification.
        </p>
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

      <div className="grid gap-4 md:grid-cols-[1fr_1.4fr]">

        {/* Liste des évaluations terminées */}
        <div className="flex flex-col gap-3">
          <Tabs value={tab} onValueChange={(v) => { setTab(v); setSelected(null) }}>
            <TabsList className="w-full rounded-xl">
              <TabsTrigger value="a_publier" className="flex-1">À publier</TabsTrigger>
              <TabsTrigger value="publiees"  className="flex-1">Publiées</TabsTrigger>
            </TabsList>
          </Tabs>

          {evaluations.isPending ? (
            <p role="status" className="py-12 text-center text-sm text-muted-foreground">Chargement des évaluations…</p>
          ) : evaluations.isError ? (
            <p role="alert" className="py-12 text-center text-sm text-destructive">
              Impossible de charger vos évaluations.{" "}
              <button type="button" onClick={() => evaluations.refetch()} className="underline">Réessayer</button>
            </p>
          ) : filtered.length === 0 ? (
            <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border py-12 text-center text-muted-foreground">
              <CheckCircle2 className="size-6 text-primary" />
              <p className="text-sm font-medium">
                {tab === "a_publier" ? "Aucune évaluation terminée en attente de publication." : "Aucun résultat publié."}
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              {filtered.map((e) => {
                const isSelected = selected === e.id
                return (
                  <button
                    key={e.id}
                    type="button"
                    aria-pressed={isSelected}
                    onClick={() => setSelected(isSelected ? null : e.id)}
                    className={[
                      "w-full cursor-pointer rounded-2xl border p-4 text-left transition-colors",
                      isSelected
                        ? "border-primary/40 bg-primary/5"
                        : "border-border/60 bg-card hover:bg-muted/30",
                    ].join(" ")}
                  >
                    <span className="flex items-start justify-between gap-2">
                      <span className="block text-[13px] font-semibold leading-snug">{e.titre}</span>
                      <Badge
                        variant="outline"
                        className={`shrink-0 rounded-full text-[11px] font-semibold ${
                          e.resultatsPublies
                            ? "bg-primary/10 text-primary border-primary/20"
                            : "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400"
                        }`}
                      >
                        {e.resultatsPublies ? "Publiés" : "À publier"}
                      </Badge>
                    </span>
                    <span className="mt-2 flex items-center gap-4 text-[11px] text-muted-foreground">
                      <span className="flex items-center gap-1"><Users className="size-3" />{e.nbCopies ?? 0} / {e.nbAttendus ?? 0} copies</span>
                      <span className="flex items-center gap-1"><FileText className="size-3" />{libelleNbQuestions(e.nbQuestions)}</span>
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Détail + publication */}
        <div>
          {!evalSelected ? (
            <div className="flex h-full flex-col items-center justify-center rounded-2xl border border-dashed border-border py-16 text-center text-muted-foreground">
              <p className="font-medium">Sélectionnez une évaluation</p>
              <p className="mt-1 text-sm">pour la publier ou consulter son état.</p>
            </div>
          ) : (
            <Card className="rounded-2xl border-border/60 shadow-sm">
              <CardHeader className="pb-2">
                <CardTitle className="text-base font-bold">{evalSelected.titre}</CardTitle>
                <p className="text-sm text-muted-foreground">
                  Fermée le {formatDate(evalSelected.dateFermeture)} · {evalSelected.nbCopies ?? 0} copie{(evalSelected.nbCopies ?? 0) > 1 ? "s" : ""} sur {evalSelected.nbAttendus ?? 0} attendue{(evalSelected.nbAttendus ?? 0) > 1 ? "s" : ""}
                </p>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">

                <NotesParEtudiant evaluationId={evalSelected.id} />

                {!evalSelected.resultatsPublies && (
                  <>
                    <div className="rounded-xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm text-muted-foreground">
                      Les étudiants ayant une copie notée verront leur note et les corrigés, et recevront une notification
                      dans l'application. Une copie pas encore notée reste « en attente » : son étudiant est notifié dès qu'elle l'est.
                    </div>
                    <p className="text-xs text-muted-foreground">
                      La publication est possible 2 minutes après la fermeture, une fois plus personne ne peut rendre sa copie.
                      Elle est définitive.
                    </p>
                    <Button
                      className="w-full rounded-xl"
                      onClick={() => publier(evalSelected.id, evalSelected.titre)}
                      disabled={publication.isPending}
                    >
                      {publication.isPending ? (
                        "Publication en cours..."
                      ) : (
                        <><Send className="size-4" /> Publier les résultats</>
                      )}
                    </Button>

                    {/* Publication différée (DS-07) : seule la date est enregistrée, le serveur publie à l'échéance */}
                    <div className="flex flex-col gap-2 rounded-xl border border-border/60 p-4">
                      <p className="flex items-center gap-2 text-sm font-semibold"><CalendarClock className="size-4 text-primary" /> Publier plus tard</p>
                      {evalSelected.publicationResultatsPrevueLe ? (
                        <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
                          <span>Publication automatique prévue le <strong>{formatDateHeure(evalSelected.publicationResultatsPrevueLe)}</strong>.</span>
                          <Button variant="outline" size="sm" className="rounded-lg" disabled={annulation.isPending}
                            onClick={() => annulation.mutate(evalSelected.id)}>
                            Annuler la programmation
                          </Button>
                        </div>
                      ) : (
                        <form className="flex flex-wrap items-end gap-3"
                          onSubmit={(ev) => { ev.preventDefault(); if (datePrevue) programmation.mutate({ id: evalSelected.id, date: datePrevue }) }}>
                          <div className="flex flex-col gap-1.5">
                            <label htmlFor="date-publication" className="text-xs text-muted-foreground">Date et heure de publication</label>
                            <Input id="date-publication" type="datetime-local" required value={datePrevue}
                              onChange={(ev) => setDatePrevue(ev.target.value)} className="rounded-lg" />
                          </div>
                          <Button type="submit" variant="outline" className="rounded-xl" disabled={!datePrevue || programmation.isPending}>
                            Programmer la publication
                          </Button>
                        </form>
                      )}
                    </div>
                  </>
                )}

                {evalSelected.resultatsPublies && (
                  <div className="flex items-center gap-2 text-sm text-primary">
                    <CheckCircle2 className="size-4" />
                    Résultats déjà publiés : les étudiants ont été notifiés.
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
