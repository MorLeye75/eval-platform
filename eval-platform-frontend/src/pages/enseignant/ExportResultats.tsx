import { useState } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { FileText, FileSpreadsheet, Download, CheckCircle2, Users, Award } from "lucide-react"
import type { Evaluation, StatutEvaluation } from "@/types"

// ============================================================
// Données mockées — à remplacer par :
// const { data } = useQuery({ queryKey: ["enseignant-evaluations-publiees"],
//   queryFn: () => api.get("/enseignant/evaluations?statut=PUBLIEE") })
// Export PDF   : GET /api/enseignant/evaluations/{id}/export/pdf
// Export Excel : GET /api/enseignant/evaluations/{id}/export/excel
// ============================================================

interface EvalExportable extends Pick<Evaluation, "id" | "titre" | "statut"> {
  nbParticipants: number
  moyenne: number
  dateCorrection: string
}

const EVALS: EvalExportable[] = [
  { id: 1, titre: "Structures de données — QCM",        statut: "PUBLIEE", nbParticipants: 42, moyenne: 13.4, dateCorrection: "2026-07-13" },
  { id: 3, titre: "Réseaux — questions ouvertes",        statut: "PUBLIEE", nbParticipants: 38, moyenne: 13.5, dateCorrection: "2026-07-05" },
  { id: 4, titre: "Bases de données — quiz chronométré", statut: "PUBLIEE", nbParticipants: 40, moyenne: 14.1, dateCorrection: "2026-07-10" },
]

const STATUT_CFG: Record<StatutEvaluation, { label: string; cls: string }> = {
  BROUILLON: { label: "Brouillon", cls: "bg-muted text-muted-foreground border-transparent"                          },
  PUBLIEE:   { label: "Publiée",   cls: "bg-primary/10 text-primary border-primary/20"                              },
  EN_COURS:  { label: "En cours",  cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"        },
  TERMINEE:  { label: "Terminée",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400"},
}

export default function ExportResultats() {
  const [loading, setLoading] = useState<string | null>(null)
  const [exported, setExported] = useState<string[]>([])

  async function handleExport(evalId: number, format: "pdf" | "excel") {
    const key = `${evalId}-${format}`
    setLoading(key)
    try {
      // TODO : const blob = await api.get(`/enseignant/evaluations/${evalId}/export/${format}`, { responseType: "blob" })
      //        const url = URL.createObjectURL(blob)
      //        const a = document.createElement("a"); a.href = url; a.download = `eval-${evalId}.${format === "pdf" ? "pdf" : "xlsx"}`; a.click()
      await new Promise(r => setTimeout(r, 1000)) // simule le téléchargement
      setExported(prev => [...prev, key])
    } finally {
      setLoading(null)
    }
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">

      {/* En-tête */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Export des résultats</h1>
        <p className="mt-1 text-muted-foreground">
          Téléchargez les résultats de vos évaluations en PDF ou Excel.
        </p>
      </div>

      {/* Légende formats */}
      <div className="grid gap-3 md:grid-cols-2">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="flex items-center gap-3 p-4">
            <span className="flex size-10 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
              <FileText className="size-5" />
            </span>
            <div>
              <p className="text-sm font-semibold">Rapport PDF</p>
              <p className="text-[11px] text-muted-foreground">
                Rapport complet par évaluation : statistiques, distribution, résultats individuels.
              </p>
            </div>
          </CardContent>
        </Card>
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="flex items-center gap-3 p-4">
            <span className="flex size-10 items-center justify-center rounded-xl bg-green-500/10 text-green-700 dark:text-green-400">
              <FileSpreadsheet className="size-5" />
            </span>
            <div>
              <p className="text-sm font-semibold">Fichier Excel / CSV</p>
              <p className="text-[11px] text-muted-foreground">
                Liste des notes par étudiant, exploitable dans un tableur.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Liste des évaluations exportables */}
      {EVALS.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border py-16 text-center text-muted-foreground">
          <CheckCircle2 className="size-8 text-primary" />
          <p className="font-medium">Aucun résultat publié pour l'instant.</p>
          <p className="text-sm">Les exports sont disponibles après publication.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {EVALS.map(e => {
            const { label, cls } = STATUT_CFG[e.statut]
            const pdfKey   = `${e.id}-pdf`
            const excelKey = `${e.id}-excel`

            return (
              <Card key={e.id} className="rounded-2xl border-border/60 shadow-sm">
                <CardContent className="flex flex-wrap items-center gap-4 p-5">

                  {/* Infos éval */}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold">{e.titre}</p>
                      <Badge variant="outline" className={`shrink-0 rounded-full text-[11px] font-semibold ${cls}`}>
                        {label}
                      </Badge>
                    </div>
                    <div className="mt-1 flex items-center gap-4 text-[11px] text-muted-foreground">
                      <span className="flex items-center gap-1">
                        <Users className="size-3" />{e.nbParticipants} étudiants
                      </span>
                      <span className="flex items-center gap-1">
                        <Award className="size-3" />Moy. {e.moyenne} / 20
                      </span>
                      <span>Corrigé le {new Date(e.dateCorrection).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" })}</span>
                    </div>
                  </div>

                  {/* Boutons export */}
                  <div className="flex shrink-0 gap-2">
                    <Button
                      variant="outline"
                      className="rounded-xl border-destructive/30 text-destructive hover:bg-destructive/10 hover:text-destructive"
                      onClick={() => handleExport(e.id, "pdf")}
                      disabled={loading === pdfKey}
                    >
                      {loading === pdfKey ? (
                        <span className="text-xs">Génération...</span>
                      ) : exported.includes(pdfKey) ? (
                        <><CheckCircle2 className="size-4" /> PDF téléchargé</>
                      ) : (
                        <><Download className="size-4" /> PDF</>
                      )}
                    </Button>
                    <Button
                      variant="outline"
                      className="rounded-xl border-green-500/30 text-green-700 hover:bg-green-500/10 hover:text-green-700 dark:text-green-400"
                      onClick={() => handleExport(e.id, "excel")}
                      disabled={loading === excelKey}
                    >
                      {loading === excelKey ? (
                        <span className="text-xs">Génération...</span>
                      ) : exported.includes(excelKey) ? (
                        <><CheckCircle2 className="size-4" /> Excel téléchargé</>
                      ) : (
                        <><Download className="size-4" /> Excel</>
                      )}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
