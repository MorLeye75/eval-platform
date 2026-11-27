import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Scale } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { CLE_EVALUATIONS_ENSEIGNANT, mesEvaluationsEnseignant } from "@/lib/evaluationsEnseignant"
import { cleComparaison, comparerEvaluations, MAX_COMPARAISON, MIN_COMPARAISON } from "@/lib/statistiquesEnseignant"
import { pourcent, sur20 } from "@/lib/adminVue"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Comparaison de 2 à 6 évaluations (DS-19). Les chiffres viennent du serveur (mêmes indicateurs que la page
// Statistiques) ; sans copie notée un indicateur vaut « — » et aucune barre n'est tracée, jamais 0.
// ============================================================

export default function ComparaisonEvaluations() {
  const [choisies, setChoisies] = useState<number[]>([])
  // Les ids réellement comparés : le résultat ne change qu'au clic sur « Comparer »
  const [comparees, setComparees] = useState<number[] | null>(null)

  const evaluations = useQuery({ queryKey: CLE_EVALUATIONS_ENSEIGNANT, queryFn: mesEvaluationsEnseignant })
  // Avant l'ouverture, aucune copie n'existe : rien à comparer
  const ouvertes = (evaluations.data ?? []).filter((e) => e.statut === "TERMINEE" || e.statut === "EN_COURS")

  const comparaison = useQuery({
    queryKey: cleComparaison(comparees ?? []),
    queryFn: () => comparerEvaluations(comparees ?? []),
    enabled: comparees !== null,
    retry: false,
  })

  function basculer(id: number) {
    setChoisies((actuelles) => (actuelles.includes(id) ? actuelles.filter((x) => x !== id) : [...actuelles, id]))
  }

  const nbValide = choisies.length >= MIN_COMPARAISON && choisies.length <= MAX_COMPARAISON

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Comparer des évaluations</h1>
        <p className="mt-1 text-muted-foreground">
          Choisissez de {MIN_COMPARAISON} à {MAX_COMPARAISON} évaluations ouvertes : les notes sont ramenées sur 20, les barèmes différents restent comparables.
        </p>
      </div>

      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-col gap-4 p-5">
          {evaluations.isPending && <p className="text-sm text-muted-foreground">Chargement…</p>}
          {evaluations.isError && <p role="alert" className="text-sm text-destructive">{messageErreur(evaluations.error, "Impossible de charger vos évaluations.")}</p>}
          {evaluations.isSuccess && ouvertes.length < MIN_COMPARAISON && (
            <p className="text-sm text-muted-foreground">Il faut au moins {MIN_COMPARAISON} évaluations ouvertes pour comparer.</p>
          )}
          {ouvertes.length >= MIN_COMPARAISON && (
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-1 text-sm font-semibold">Évaluations ({choisies.length} choisie{choisies.length > 1 ? "s" : ""})</legend>
              {ouvertes.map((e) => {
                const coche = choisies.includes(e.id)
                return (
                  <label key={e.id} className="flex items-center gap-3 rounded-lg px-2 py-1.5 text-sm hover:bg-muted/50">
                    <input
                      type="checkbox"
                      className="size-4 accent-primary"
                      checked={coche}
                      disabled={!coche && choisies.length >= MAX_COMPARAISON}
                      onChange={() => basculer(e.id)}
                    />
                    {e.titre}
                  </label>
                )
              })}
            </fieldset>
          )}
          <div className="flex items-center gap-3">
            <Button className="rounded-xl" disabled={!nbValide} onClick={() => setComparees([...choisies])}>
              <Scale className="size-4" /> Comparer
            </Button>
            {!nbValide && ouvertes.length >= MIN_COMPARAISON && (
              <span className="text-xs text-muted-foreground">Cochez au moins {MIN_COMPARAISON} évaluations.</span>
            )}
          </div>
        </CardContent>
      </Card>

      {comparees !== null && comparaison.isPending && <p className="text-sm text-muted-foreground">Calcul…</p>}
      {comparaison.isError && (
        <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {messageErreur(comparaison.error, "La comparaison a échoué.")}
        </p>
      )}
      {comparaison.isSuccess && (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <caption className="sr-only">Comparaison des évaluations</caption>
                <thead>
                  <tr className="border-b border-border text-left text-xs text-muted-foreground">
                    <th scope="col" className="px-4 py-3 font-semibold">Évaluation</th>
                    <th scope="col" className="px-4 py-3 text-right font-semibold">Copies notées</th>
                    <th scope="col" className="px-4 py-3 font-semibold">Moyenne sur 20</th>
                    <th scope="col" className="px-4 py-3 text-right font-semibold">Médiane</th>
                    <th scope="col" className="px-4 py-3 text-right font-semibold">Réussite</th>
                  </tr>
                </thead>
                <tbody>
                  {comparaison.data.evaluations.map((l) => (
                    <tr key={l.evaluationId} className="border-b border-border/60 last:border-0">
                      <th scope="row" className="px-4 py-3 text-left font-medium">{l.titre}</th>
                      <td className="px-4 py-3 text-right">{l.nbNotees} / {l.nbAttendus}</td>
                      <td className="px-4 py-3">
                        {l.moyenneSur20 === null ? (
                          "—"
                        ) : (
                          <div className="flex items-center gap-3">
                            <div
                              role="img"
                              aria-label={`Moyenne ${sur20(l.moyenneSur20)} : ${l.titre}`}
                              className="h-2.5 w-40 overflow-hidden rounded-full bg-muted"
                            >
                              <div className="h-full rounded-full bg-primary" style={{ width: `${Math.min(100, Math.max(0, (l.moyenneSur20 / 20) * 100))}%` }} />
                            </div>
                            <span className="font-semibold">{sur20(l.moyenneSur20)}</span>
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">{sur20(l.medianeSur20)}</td>
                      <td className="px-4 py-3 text-right">{pourcent(l.tauxReussite)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
