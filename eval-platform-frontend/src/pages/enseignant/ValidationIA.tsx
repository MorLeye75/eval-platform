import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { CheckCircle2, XCircle, ChevronDown, ChevronUp, Bot, User, Sparkles } from "lucide-react"
import type { StatutProposition } from "@/types"
import {
  CLE_CORRECTIONS_IA,
  genererPropositionsIA,
  listerCorrectionsIA,
  rejeterProposition,
  validerProposition,
  type NoteApi,
  type PropositionIAApi,
} from "@/lib/correctionsIA"
import { CLE_EVALUATIONS_ENSEIGNANT, mesEvaluationsEnseignant } from "@/lib/evaluationsEnseignant"
import { messageErreur } from "@/lib/passation"
import NotesManuelles from "./NotesManuelles"

// ============================================================
// Validation des corrections IA (DS-08). L'IA propose une note par réponse ouverte ; la note retenue est
// toujours celle de l'enseignant (valider = reprendre la note de l'IA, rejeter = saisir la sienne).
// ============================================================

const STATUT_CONFIG: Record<StatutProposition, { label: string; cls: string }> = {
  EN_ATTENTE: { label: "En attente",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400" },
  VALIDEE:    { label: "Validée",     cls: "bg-primary/10 text-primary border-primary/20"                              },
  REJETEE:    { label: "Rejetée",     cls: "bg-destructive/10 text-destructive border-destructive/20"                  },
}

function bilan(r: NoteApi) {
  return r.copieEvaluee
    ? `Note enregistrée. Copie terminée : note finale ${r.noteFinale}.`
    : "Note enregistrée."
}

export default function ValidationIA() {
  const [tab,      setTab]      = useState("EN_ATTENTE")
  const [expanded, setExpanded] = useState<number | null>(null)
  const [notes,    setNotes]    = useState<Record<number, string>>({})
  const [erreurs,  setErreurs]  = useState<Record<number, string | undefined>>({})
  const [erreur,   setErreur]   = useState<string | null>(null)
  const [info,     setInfo]     = useState<string | null>(null)
  const [aGenerer, setAGenerer] = useState("")
  const queryClient = useQueryClient()

  const corrections = useQuery({
    queryKey: CLE_CORRECTIONS_IA,
    queryFn: listerCorrectionsIA,
    // Tant que des propositions se génèrent, on relit toutes les 5 s (aucune donnée n'est poussée par le serveur)
    refetchInterval: (q) => ((q.state.data?.enCours ?? 0) > 0 ? 5000 : false),
  })
  const evaluations = useQuery({ queryKey: CLE_EVALUATIONS_ENSEIGNANT, queryFn: mesEvaluationsEnseignant })
  const terminees = (evaluations.data ?? []).filter((e) => e.statut === "TERMINEE")

  const generation = useMutation({
    mutationFn: (evaluationId: number) => genererPropositionsIA(evaluationId),
    onSuccess: (r) => {
      setErreur(null)
      setInfo(r.nbReponsesAProposer === 0
        ? "Aucune réponse à proposer : tout est déjà traité."
        : `${r.nbReponsesAProposer} proposition${r.nbReponsesAProposer > 1 ? "s" : ""} en cours de génération.`)
    },
    onError: (e) => { setInfo(null); setErreur(messageErreur(e, "La génération des propositions a échoué.")) },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLE_CORRECTIONS_IA }),
  })

  const decision = useMutation({
    mutationFn: ({ id, statut, note }: { id: number; statut: "VALIDEE" | "REJETEE"; note?: number }) =>
      statut === "VALIDEE" ? validerProposition(id, note) : rejeterProposition(id, note ?? 0),
    onSuccess: (r) => { setErreur(null); setInfo(bilan(r)); setExpanded(null) },
    onError: (e) => { setInfo(null); setErreur(messageErreur(e, "La décision n'a pas pu être enregistrée.")) },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLE_CORRECTIONS_IA }),
  })

  const toutes = corrections.data?.propositions ?? []
  const filtered = toutes.filter((c) => tab === "toutes" || c.statut === tab)
  const nbEnAttente = toutes.filter((c) => c.statut === "EN_ATTENTE").length
  const enCours = corrections.data?.enCours ?? 0

  /** Note saisie, ou `defaut` si le champ est vide ; null (avec message) si elle sort de [0, pointsMax]. */
  function lireNote(c: PropositionIAApi, defaut: number | undefined): number | undefined | null {
    const saisie = (notes[c.id] ?? "").trim()
    if (saisie === "") return defaut
    const note = Number(saisie.replace(",", "."))
    if (!Number.isFinite(note) || note < 0 || note > c.pointsMax) {
      setErreurs((e) => ({ ...e, [c.id]: `La note doit être comprise entre 0 et ${c.pointsMax}.` }))
      return null
    }
    return note
  }

  function traiter(c: PropositionIAApi, statut: "VALIDEE" | "REJETEE") {
    // Valider sans saisie : la note de l'IA (le serveur la reprend) ; rejeter exige la note de l'enseignant
    const note = lireNote(c, undefined)
    if (note === null) return
    if (statut === "REJETEE" && note === undefined) {
      setErreurs((e) => ({ ...e, [c.id]: "Saisissez votre note pour rejeter la proposition." }))
      return
    }
    decision.mutate({ id: c.id, statut, note })
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Validation des corrections IA</h1>
          <p className="mt-1 text-muted-foreground">
            L'IA propose une note ; c'est vous qui décidez. Aucune note n'est enregistrée sans votre validation.
          </p>
        </div>
        {nbEnAttente > 0 && (
          <Badge variant="outline" className="rounded-full bg-yellow-500/10 text-yellow-700 border-yellow-500/20 px-3 py-1.5 text-sm font-semibold">
            {nbEnAttente} en attente
          </Badge>
        )}
      </div>

      {/* Génération des propositions d'une évaluation terminée */}
      <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-border bg-card p-3">
        <label htmlFor="evaluation-a-generer" className="text-sm font-medium">Évaluation terminée :</label>
        <select
          id="evaluation-a-generer"
          value={aGenerer}
          onChange={(e) => setAGenerer(e.target.value)}
          className="h-9 min-w-0 flex-1 rounded-lg border border-input bg-background px-3 text-sm"
        >
          <option value="">Choisir une évaluation…</option>
          {terminees.map((e) => <option key={e.id} value={e.id}>{e.titre}</option>)}
        </select>
        <Button
          className="rounded-xl"
          disabled={aGenerer === "" || generation.isPending}
          onClick={() => generation.mutate(Number(aGenerer))}
        >
          <Sparkles className="size-4" /> Générer les propositions
        </Button>
      </div>

      {enCours > 0 && (
        <p role="status" className="text-sm text-muted-foreground">
          {enCours} réponse{enCours > 1 ? "s" : ""} sans proposition (génération en cours, ou modèle indisponible : vous pouvez noter à la main).
        </p>
      )}
      {info && <p role="status" className="rounded-xl bg-primary/10 px-4 py-2.5 text-sm text-primary">{info}</p>}
      {erreur && <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{erreur}</p>}

      {/* Onglets */}
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="rounded-xl">
          <TabsTrigger value="EN_ATTENTE">En attente ({nbEnAttente})</TabsTrigger>
          <TabsTrigger value="VALIDEE">Validées</TabsTrigger>
          <TabsTrigger value="REJETEE">Rejetées</TabsTrigger>
          <TabsTrigger value="toutes">Toutes</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Liste */}
      {corrections.isError ? (
        <p role="alert" className="text-sm text-destructive">{messageErreur(corrections.error, "Impossible de charger les propositions.")}</p>
      ) : corrections.isLoading ? (
        <p className="text-sm text-muted-foreground">Chargement…</p>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border py-16 text-center text-muted-foreground">
          <CheckCircle2 className="size-8 text-primary" />
          <p className="font-medium">Aucune correction dans cette catégorie.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {filtered.map((c) => {
            const isOpen = expanded === c.id
            const { label, cls } = STATUT_CONFIG[c.statut]
            const initiales = c.etudiant.split(" ").map((m) => m[0] ?? "").join("").slice(0, 2).toUpperCase()

            return (
              <Card key={c.id} className="rounded-2xl border-border/60 shadow-sm">
                {/* En-tête : bouton qui ouvre ou ferme le détail */}
                <button
                  type="button"
                  aria-expanded={isOpen}
                  className="flex w-full cursor-pointer items-center gap-3 p-4 text-left"
                  onClick={() => setExpanded(isOpen ? null : c.id)}
                >
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                    {initiales}
                  </span>
                  <span className="block min-w-0 flex-1">
                    <span className="flex items-center gap-2">
                      <span className="text-[13px] font-semibold">{c.etudiant}</span>
                      <Badge variant="outline" className={`rounded-full text-[11px] font-semibold ${cls}`}>
                        {label}
                      </Badge>
                    </span>
                    <span className="block mt-0.5 truncate text-[11px] text-muted-foreground">
                      {c.evaluation} · {c.question.slice(0, 60)}…
                    </span>
                  </span>
                  <span className="flex shrink-0 items-center gap-3">
                    <span className="block text-right">
                      <span className="flex items-center gap-1 text-sm font-bold">
                        <Bot className="size-3.5 text-primary" />
                        {c.noteProposee} / {c.pointsMax}
                      </span>
                      <span className="block text-[11px] text-muted-foreground">note IA</span>
                    </span>
                    {isOpen ? <ChevronUp className="size-4 text-muted-foreground" /> : <ChevronDown className="size-4 text-muted-foreground" />}
                  </span>
                </button>

                {/* Détail déplié */}
                {isOpen && (
                  <CardContent className="flex flex-col gap-4 border-t border-border pt-4">

                    <div>
                      <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">Question</p>
                      <p className="text-sm">{c.question}</p>
                    </div>

                    <div>
                      <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        <User className="size-3.5" /> Réponse de l'étudiant
                      </p>
                      {/* Texte de l'étudiant : affiché tel quel (échappé par React), jamais interprété */}
                      <div className="whitespace-pre-wrap rounded-xl border border-border bg-muted/30 px-4 py-3 text-sm">
                        {c.reponse?.trim() ? c.reponse : "Aucune réponse."}
                      </div>
                    </div>

                    <div>
                      <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        <Bot className="size-3.5" /> Analyse de l'IA
                      </p>
                      <div className="rounded-xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm text-muted-foreground">
                        {c.justification ?? "Aucune justification."}
                      </div>
                    </div>

                    {c.statut === "EN_ATTENTE" && (
                      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-background p-3">
                        <div className="flex items-center gap-2">
                          <label htmlFor={`note-${c.id}`} className="text-sm font-medium">Note finale :</label>
                          <Input
                            id={`note-${c.id}`}
                            type="number"
                            min={0}
                            max={c.pointsMax}
                            step={0.5}
                            placeholder={String(c.noteProposee)}
                            value={notes[c.id] ?? ""}
                            onChange={(e) => {
                              setNotes((n) => ({ ...n, [c.id]: e.target.value }))
                              setErreurs((er) => ({ ...er, [c.id]: undefined }))
                            }}
                            aria-invalid={!!erreurs[c.id]}
                            className="w-24 rounded-lg"
                          />
                          <span className="text-sm text-muted-foreground">/ {c.pointsMax}</span>
                        </div>
                        {erreurs[c.id] && (
                          <p className="w-full text-xs text-destructive">{erreurs[c.id]}</p>
                        )}
                        <div className="ml-auto flex gap-2">
                          <Button
                            variant="outline"
                            disabled={decision.isPending}
                            className="rounded-xl border-destructive/30 text-destructive hover:bg-destructive/10 hover:text-destructive"
                            onClick={() => traiter(c, "REJETEE")}
                          >
                            <XCircle className="size-4" /> Rejeter
                          </Button>
                          <Button className="rounded-xl" disabled={decision.isPending} onClick={() => traiter(c, "VALIDEE")}>
                            <CheckCircle2 className="size-4" /> Valider
                          </Button>
                        </div>
                      </div>
                    )}

                    {c.statut !== "EN_ATTENTE" && (
                      <div className="flex items-center gap-2 rounded-xl bg-muted/40 px-4 py-2.5 text-sm text-muted-foreground">
                        <CheckCircle2 className="size-4 text-primary" />
                        Note attribuée : <span className="font-bold text-foreground">{c.noteRetenue ?? c.noteProposee} / {c.pointsMax}</span>
                        {c.noteRetenue !== null && c.noteRetenue !== c.noteProposee && (
                          <span className="text-xs">(proposition IA : {c.noteProposee})</span>
                        )}
                      </div>
                    )}

                  </CardContent>
                )}
              </Card>
            )
          })}
        </div>
      )}

      <NotesManuelles onBilan={(r) => { setErreur(null); setInfo(bilan(r)) }} />
    </div>
  )
}
