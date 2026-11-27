import { useState } from "react"
import { Link, useParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ArrowLeft, Lock } from "lucide-react"
import {
  CLE_EVALUATIONS_ENSEIGNANT,
  cleAmenagements,
  definirAmenagement,
  erreurDuree,
  listerAmenagements,
  supprimerAmenagement,
  type AmenagementsEvaluationApi,
  type EtudiantAmenageApi,
} from "@/lib/evaluationsEnseignant"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Tiers-temps d'une évaluation (DS-06). Le serveur reste juge : la liste indique ce qu'il acceptera (`modifiable`),
// la durée saisie est contrôlée ici seulement pour éviter un aller-retour inutile.
// ============================================================

export default function TiersTemps() {
  const { id = "" } = useParams<{ id: string }>()
  const client = useQueryClient()
  const [erreur, setErreur] = useState<string | null>(null)

  const liste = useQuery({ queryKey: cleAmenagements(id), queryFn: () => listerAmenagements(id) })

  const apres = () => {
    setErreur(null)
    void client.invalidateQueries({ queryKey: cleAmenagements(id) })
    void client.invalidateQueries({ queryKey: CLE_EVALUATIONS_ENSEIGNANT })
  }
  const enregistrer = useMutation({
    mutationFn: (v: { etudiantId: number; duree: number }) => definirAmenagement(Number(id), v.etudiantId, v.duree),
    onSuccess: apres,
    onError: (e) => setErreur(messageErreur(e, "Impossible d'enregistrer le tiers-temps.")),
  })
  const retirer = useMutation({
    mutationFn: (etudiantId: number) => supprimerAmenagement(Number(id), etudiantId),
    onSuccess: apres,
    onError: (e) => setErreur(messageErreur(e, "Impossible de retirer le tiers-temps.")),
  })

  if (liste.isError) {
    return <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
      {messageErreur(liste.error, "Impossible de charger les étudiants de l'évaluation.")}
    </p>
  }
  if (!liste.data) return <p className="text-sm text-muted-foreground">Chargement…</p>

  const d: AmenagementsEvaluationApi = liste.data
  const nbAmenages = d.etudiants.filter((e) => e.dureeMinutes !== null).length

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div>
        <Link to="/enseignant/evaluations" className="mb-2 inline-flex items-center gap-1 text-xs text-primary">
          <ArrowLeft className="size-3" aria-hidden /> Mes évaluations
        </Link>
        <h1 className="text-3xl font-extrabold tracking-tight">Tiers-temps</h1>
        <p className="mt-1 text-muted-foreground">
          Durée standard : {d.dureeStandard} min. Un tiers-temps ne peut qu'augmenter la durée, dans la limite de la plage
          d'ouverture ({d.plageMinutes} min). Il est verrouillé dès que l'étudiant a commencé sa copie.
        </p>
      </div>

      {erreur && (
        <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{erreur}</p>
      )}

      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="p-0">
          <p className="px-6 pt-4 text-sm text-muted-foreground">
            {d.etudiants.length} étudiant{d.etudiants.length > 1 ? "s" : ""} attendu{d.etudiants.length > 1 ? "s" : ""},
            {" "}{nbAmenages} avec un tiers-temps.
          </p>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Étudiant</TableHead>
                <TableHead>Durée accordée</TableHead>
                <TableHead className="text-right">Tiers-temps</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {d.etudiants.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={3} className="py-10 text-center text-muted-foreground">
                    Aucun étudiant n'est inscrit dans les promotions de cette évaluation.
                  </TableCell>
                </TableRow>
              ) : (
                d.etudiants.map((e) => (
                  <LigneEtudiant
                    key={e.etudiantId}
                    etudiant={e}
                    dureeStandard={d.dureeStandard}
                    plageMinutes={d.plageMinutes}
                    occupe={enregistrer.isPending || retirer.isPending}
                    onEnregistrer={(duree) => enregistrer.mutate({ etudiantId: e.etudiantId, duree })}
                    onRetirer={() => retirer.mutate(e.etudiantId)}
                    onErreur={setErreur}
                  />
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}

function LigneEtudiant({ etudiant: e, dureeStandard, plageMinutes, occupe, onEnregistrer, onRetirer, onErreur }: {
  etudiant: EtudiantAmenageApi
  dureeStandard: number
  plageMinutes: number
  occupe: boolean
  onEnregistrer: (duree: number) => void
  onRetirer: () => void
  onErreur: (message: string | null) => void
}) {
  const [saisie, setSaisie] = useState(e.dureeMinutes === null ? "" : String(e.dureeMinutes))
  const nomComplet = `${e.prenom} ${e.nom}`

  const valider = () => {
    const message = erreurDuree(saisie, dureeStandard, plageMinutes)
    onErreur(message)
    if (message === null) onEnregistrer(Number(saisie))
  }

  return (
    <TableRow>
      <TableCell>
        <div className="font-semibold">{nomComplet}</div>
        {e.numEtudiant && <div className="text-[11px] text-muted-foreground">{e.numEtudiant}</div>}
      </TableCell>
      <TableCell className="text-sm">
        {e.dureeMinutes === null
          ? <span className="text-muted-foreground">Standard ({dureeStandard} min)</span>
          : <span className="font-semibold text-primary">{e.dureeMinutes} min</span>}
      </TableCell>
      <TableCell className="text-right">
        {e.modifiable ? (
          <div className="flex items-center justify-end gap-2">
            <Input
              type="number"
              inputMode="numeric"
              min={dureeStandard}
              max={plageMinutes}
              value={saisie}
              onChange={(ev) => setSaisie(ev.target.value)}
              aria-label={`Durée en minutes : ${nomComplet}`}
              className="h-8 w-24 rounded-lg"
            />
            <Button size="sm" className="rounded-lg" disabled={occupe} onClick={valider}
                    aria-label={`Enregistrer le tiers-temps : ${nomComplet}`}>
              Enregistrer
            </Button>
            {e.dureeMinutes !== null && (
              <Button size="sm" variant="outline" className="rounded-lg" disabled={occupe} onClick={() => { setSaisie(""); onErreur(null); onRetirer() }}
                      aria-label={`Retirer le tiers-temps : ${nomComplet}`}>
                Retirer
              </Button>
            )}
          </div>
        ) : (
          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            <Lock className="size-3" aria-hidden /> Verrouillé (copie commencée ou évaluation terminée)
          </span>
        )}
      </TableCell>
    </TableRow>
  )
}
