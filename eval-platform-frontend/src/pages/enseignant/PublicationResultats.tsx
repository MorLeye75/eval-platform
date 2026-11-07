import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import { Send, Users, Award, CheckCircle2 } from "lucide-react"
import type { Evaluation, StatutEvaluation } from "@/types"

// ============================================================
// Données mockées — à remplacer par :
// const { data: evals } = useQuery({ queryKey: ["enseignant-evaluations-terminees"],
//   queryFn: () => api.get("/enseignant/evaluations?statut=TERMINEE,CORRIGEE") })
// Publication : POST /api/enseignant/evaluations/{id}/publier-resultats
// ============================================================

interface EvalAvecResultats extends Pick<Evaluation, "id" | "titre" | "statut"> {
  nbParticipants: number
  moyenne: number
  resultatsPublies: boolean
  dateCorrection: string
}

const EVALS: EvalAvecResultats[] = [
  { id: 1, titre: "Structures de données — QCM",        statut: "TERMINEE", nbParticipants: 42, moyenne: 13.4, resultatsPublies: false, dateCorrection: "2026-07-13" },
  { id: 2, titre: "Génie logiciel — questions ouvertes", statut: "TERMINEE", nbParticipants: 38, moyenne: 11.8, resultatsPublies: false, dateCorrection: "2026-07-14" },
  { id: 3, titre: "Réseaux — questions ouvertes",        statut: "PUBLIEE",  nbParticipants: 38, moyenne: 13.5, resultatsPublies: true,  dateCorrection: "2026-07-05" },
  { id: 4, titre: "Bases de données — quiz",             statut: "PUBLIEE",  nbParticipants: 40, moyenne: 14.1, resultatsPublies: true,  dateCorrection: "2026-07-10" },
]

interface ResultatEtudiant {
  id: number
  nom: string
  prenom: string
  note: number
  noteMax: number
  statut: "VALIDE" | "ECHEC"
}

const RESULTATS_MOCK: ResultatEtudiant[] = [
  { id: 1, nom: "Diop",   prenom: "Moussa",   note: 15.0, noteMax: 20, statut: "VALIDE" },
  { id: 2, nom: "Ndiaye", prenom: "Awa",       note: 17.5, noteMax: 20, statut: "VALIDE" },
  { id: 3, nom: "Ba",     prenom: "Cheikh",    note: 8.0,  noteMax: 20, statut: "ECHEC"  },
  { id: 4, nom: "Sy",     prenom: "Mariama",   note: 14.0, noteMax: 20, statut: "VALIDE" },
  { id: 5, nom: "Fall",   prenom: "Ibrahima",  note: 11.5, noteMax: 20, statut: "VALIDE" },
  { id: 6, nom: "Mbaye",  prenom: "Sokhna",    note: 19.0, noteMax: 20, statut: "VALIDE" },
]

const STATUT_CFG: Record<StatutEvaluation, { label: string; cls: string }> = {
  BROUILLON: { label: "Brouillon", cls: "bg-muted text-muted-foreground border-transparent"                          },
  PUBLIEE:   { label: "Publiée",   cls: "bg-primary/10 text-primary border-primary/20"                              },
  EN_COURS:  { label: "En cours",  cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"        },
  TERMINEE:  { label: "Terminée",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400"},
}

export default function PublicationResultats() {
  const [evals, setEvals]         = useState(EVALS)
  const [selected, setSelected]   = useState<number | null>(null)
  const [tab, setTab]             = useState("a_publier")
  const [publishing, setPublishing] = useState<number | null>(null)

  const filtered = evals.filter(e =>
    tab === "a_publier" ? !e.resultatsPublies : e.resultatsPublies
  )

  const evalSelected = evals.find(e => e.id === selected)

  async function handlePublier(id: number) {
    setPublishing(id)
    try {
      // TODO : await api.post(`/enseignant/evaluations/${id}/publier-resultats`)
      await new Promise(r => setTimeout(r, 800)) // simule l'appel API
      setEvals(es => es.map(e => e.id === id
        ? { ...e, resultatsPublies: true, statut: "PUBLIEE" as StatutEvaluation }
        : e
      ))
      if (selected === id) setSelected(null)
    } finally {
      setPublishing(null)
    }
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">

      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Publication des résultats</h1>
        <p className="mt-1 text-muted-foreground">
          Publiez les résultats pour notifier les étudiants de la promotion.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-[1fr_1.4fr]">

        {/* Liste des évaluations */}
        <div className="flex flex-col gap-3">
          <Tabs value={tab} onValueChange={setTab}>
            <TabsList className="w-full rounded-xl">
              <TabsTrigger value="a_publier" className="flex-1">À publier</TabsTrigger>
              <TabsTrigger value="publiees"  className="flex-1">Publiées</TabsTrigger>
            </TabsList>
          </Tabs>

          {filtered.length === 0 ? (
            <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border py-12 text-center text-muted-foreground">
              <CheckCircle2 className="size-6 text-primary" />
              <p className="text-sm font-medium">
                {tab === "a_publier" ? "Aucune évaluation en attente." : "Aucun résultat publié."}
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              {filtered.map(e => {
                const { label, cls } = STATUT_CFG[e.statut]
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
                      <Badge variant="outline" className={`shrink-0 rounded-full text-[11px] font-semibold ${cls}`}>
                        {label}
                      </Badge>
                    </span>
                    <span className="block mt-2 flex items-center gap-4 text-[11px] text-muted-foreground">
                      <span className="flex items-center gap-1"><Users className="size-3" />{e.nbParticipants} étudiants</span>
                      <span className="flex items-center gap-1"><Award className="size-3" />Moy. {e.moyenne} / 20</span>
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
              <p className="mt-1 text-sm">pour voir les résultats avant publication.</p>
            </div>
          ) : (
            <Card className="rounded-2xl border-border/60 shadow-sm">
              <CardHeader className="pb-2">
                <CardTitle className="text-base font-bold">{evalSelected.titre}</CardTitle>
                <p className="text-sm text-muted-foreground">
                  {evalSelected.nbParticipants} étudiants · Moy. {evalSelected.moyenne} / 20
                </p>
              </CardHeader>
              <CardContent className="flex flex-col gap-4 p-0">

                {/* Table résultats */}
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Étudiant</TableHead>
                      <TableHead>Note</TableHead>
                      <TableHead>Résultat</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {RESULTATS_MOCK.map(r => (
                      <TableRow key={r.id}>
                        <TableCell>
                          <span className="font-semibold">{r.prenom} {r.nom}</span>
                        </TableCell>
                        <TableCell className="font-bold">
                          {r.note} / {r.noteMax}
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant="outline"
                            className={`rounded-full text-[11px] font-semibold ${
                              r.statut === "VALIDE"
                                ? "bg-primary/10 text-primary border-primary/20"
                                : "bg-destructive/10 text-destructive border-destructive/20"
                            }`}
                          >
                            {r.statut === "VALIDE" ? "Validé" : "Échec"}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>

                {/* Bouton publication */}
                {!evalSelected.resultatsPublies && (
                  <div className="border-t border-border px-6 pb-5 pt-4">
                    <div className="mb-3 rounded-xl bg-primary/5 border border-primary/20 px-4 py-3 text-sm text-muted-foreground">
                      <span className="font-semibold text-foreground">
                        {evalSelected.nbParticipants} étudiants
                      </span>{" "}
                      seront notifiés par email à la publication.
                    </div>
                    <Button
                      className="w-full rounded-xl"
                      onClick={() => handlePublier(evalSelected.id)}
                      disabled={publishing === evalSelected.id}
                    >
                      {publishing === evalSelected.id ? (
                        "Publication en cours..."
                      ) : (
                        <><Send className="size-4" /> Publier les résultats</>
                      )}
                    </Button>
                  </div>
                )}

                {evalSelected.resultatsPublies && (
                  <div className="flex items-center gap-2 border-t border-border px-6 pb-5 pt-4 text-sm text-primary">
                    <CheckCircle2 className="size-4" />
                    Résultats déjà publiés — étudiants notifiés.
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
