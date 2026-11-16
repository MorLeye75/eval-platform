import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { FileSpreadsheet, Download, CheckCircle2, Users } from "lucide-react"
import type { StatutEvaluation } from "@/types"
import { CLE_EVALUATIONS_ENSEIGNANT, mesEvaluationsEnseignant } from "@/lib/evaluationsEnseignant"
import { exporterResultatsCsv, telechargerFichier } from "@/lib/statistiquesEnseignant"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Export des résultats (DS-09) : un fichier CSV par évaluation, une ligne par étudiant attendu (absents compris).
// Il contient des données personnelles : il n'est jamais conservé par le navigateur au-delà du téléchargement.
// ============================================================

const STATUT_CFG: Partial<Record<StatutEvaluation, { label: string; cls: string }>> = {
  EN_COURS: { label: "En cours", cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400" },
  TERMINEE: { label: "Terminée", cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400" },
}

export default function ExportResultats() {
  const [enCours, setEnCours] = useState<number | null>(null)
  const [exportes, setExportes] = useState<number[]>([])
  const [erreur, setErreur] = useState<string | null>(null)

  const evaluations = useQuery({ queryKey: CLE_EVALUATIONS_ENSEIGNANT, queryFn: mesEvaluationsEnseignant })
  // Avant l'ouverture, aucune copie n'existe : rien à exporter
  const exportables = (evaluations.data ?? []).filter((e) => e.statut === "TERMINEE" || e.statut === "EN_COURS")

  async function exporter(id: number) {
    setEnCours(id)
    setErreur(null)
    try {
      telechargerFichier(await exporterResultatsCsv(id), `resultats-${id}.csv`)
      setExportes((prev) => (prev.includes(id) ? prev : [...prev, id]))
    } catch (e) {
      // Une erreur renvoyée en blob n'est pas lisible comme JSON : message générique
      setErreur(messageErreur(e, "L'export a échoué."))
    } finally {
      setEnCours(null)
    }
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">

      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Export des résultats</h1>
        <p className="mt-1 text-muted-foreground">
          Téléchargez les résultats de vos évaluations au format CSV, qui s'ouvre dans Excel ou tout autre tableur.
        </p>
      </div>

      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex items-center gap-3 p-4">
          <span className="flex size-10 items-center justify-center rounded-xl bg-green-500/10 text-green-700 dark:text-green-400">
            <FileSpreadsheet className="size-5" />
          </span>
          <div>
            <p className="text-sm font-semibold">Fichier CSV</p>
            <p className="text-[11px] text-muted-foreground">
              Une ligne par étudiant attendu : numéro, identité, état de la copie, note et note sur 20. Les absents sont indiqués ;
              la note reste vide tant que la copie n'est pas corrigée. Fichier confidentiel : il contient des données personnelles.
            </p>
          </div>
        </CardContent>
      </Card>

      {erreur && <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{erreur}</p>}

      {evaluations.isError ? (
        <p role="alert" className="text-sm text-destructive">{messageErreur(evaluations.error, "Impossible de charger les évaluations.")}</p>
      ) : evaluations.isLoading ? (
        <p className="text-sm text-muted-foreground">Chargement…</p>
      ) : exportables.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border py-16 text-center text-muted-foreground">
          <CheckCircle2 className="size-8 text-primary" />
          <p className="font-medium">Aucune évaluation à exporter pour l'instant.</p>
          <p className="text-sm">Les exports sont disponibles dès l'ouverture d'une évaluation.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {exportables.map((e) => {
            const cfg = STATUT_CFG[e.statut]
            return (
              <Card key={e.id} className="rounded-2xl border-border/60 shadow-sm">
                <CardContent className="flex flex-wrap items-center gap-4 p-5">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold">{e.titre}</p>
                      {cfg && <Badge variant="outline" className={`shrink-0 rounded-full text-[11px] font-semibold ${cfg.cls}`}>{cfg.label}</Badge>}
                    </div>
                    <div className="mt-1 flex items-center gap-4 text-[11px] text-muted-foreground">
                      <span className="flex items-center gap-1"><Users className="size-3" />{e.nbCopies ?? 0} copie{(e.nbCopies ?? 0) > 1 ? "s" : ""} sur {e.nbAttendus ?? 0} attendus</span>
                      <span>Fermeture : {new Date(e.dateFermeture).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" })}</span>
                    </div>
                  </div>
                  <Button
                    variant="outline"
                    className="shrink-0 rounded-xl border-green-500/30 text-green-700 hover:bg-green-500/10 hover:text-green-700 dark:text-green-400"
                    aria-label={`Télécharger le CSV : ${e.titre}`}
                    onClick={() => exporter(e.id)}
                    disabled={enCours === e.id}
                  >
                    {enCours === e.id ? (
                      <span className="text-xs">Génération...</span>
                    ) : exportes.includes(e.id) ? (
                      <><CheckCircle2 className="size-4" /> Téléchargé</>
                    ) : (
                      <><Download className="size-4" /> CSV</>
                    )}
                  </Button>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
