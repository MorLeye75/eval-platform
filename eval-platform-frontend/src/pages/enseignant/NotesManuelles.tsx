import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { PenLine } from "lucide-react"
import {
  CLE_CORRECTIONS_IA,
  CLE_NOTES_MANUELLES,
  lireNoteSaisie,
  listerANoterAlaMain,
  noterReponse,
  type NoteApi,
  type ReponseANoterApi,
} from "@/lib/correctionsIA"
import { messageErreur } from "@/lib/passation"

// Réponses sans proposition de l'IA (modèle indisponible, génération non lancée) : l'enseignant les note à la main
// (DS-08, section E). Le serveur reste juge de la note (bornée à [0, barème]) et du verrou de la copie.

export default function NotesManuelles({ onBilan }: { onBilan: (r: NoteApi) => void }) {
  const client = useQueryClient()
  const [notes, setNotes] = useState<Record<number, string>>({})
  const [erreurs, setErreurs] = useState<Record<number, string | undefined>>({})
  const [erreur, setErreur] = useState<string | null>(null)

  const liste = useQuery({ queryKey: CLE_NOTES_MANUELLES, queryFn: listerANoterAlaMain })
  const noter = useMutation({
    mutationFn: (v: { reponseId: number; note: number }) => noterReponse(v.reponseId, v.note),
    onSuccess: (r) => { setErreur(null); onBilan(r) },
    onError: (e) => setErreur(messageErreur(e, "La note n'a pas pu être enregistrée.")),
    onSettled: () => {
      void client.invalidateQueries({ queryKey: CLE_NOTES_MANUELLES })
      void client.invalidateQueries({ queryKey: CLE_CORRECTIONS_IA })
    },
  })

  if (liste.isError) {
    return <p role="alert" className="text-sm text-destructive">{messageErreur(liste.error, "Impossible de charger les réponses à noter.")}</p>
  }
  const reponses = liste.data ?? []
  if (reponses.length === 0) return null

  function valider(r: ReponseANoterApi) {
    const note = lireNoteSaisie(notes[r.reponseId] ?? "", r.pointsMax)
    if (note === null) {
      setErreurs((e) => ({ ...e, [r.reponseId]: `Saisissez une note comprise entre 0 et ${r.pointsMax}.` }))
      return
    }
    noter.mutate({ reponseId: r.reponseId, note })
  }

  return (
    <section aria-labelledby="titre-notes-manuelles" className="flex flex-col gap-3">
      <h2 id="titre-notes-manuelles" className="flex items-center gap-2 font-semibold">
        <PenLine className="size-4 text-primary" aria-hidden /> À noter à la main ({reponses.length})
      </h2>
      <p className="text-sm text-muted-foreground">
        Réponses sans proposition de l'IA. Une proposition qui arriverait ensuite pour une réponse déjà notée est ignorée.
      </p>
      {erreur && <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{erreur}</p>}

      {reponses.map((r) => (
        <div key={r.reponseId} className="flex flex-col gap-3 rounded-2xl border border-border bg-card p-4">
          <div>
            <p className="text-[13px] font-semibold">{r.etudiant}</p>
            <p className="text-[11px] text-muted-foreground">{r.evaluation}</p>
          </div>
          <p className="text-sm"><span className="font-semibold">Question : </span>{r.question}</p>
          {/* Texte de l'étudiant : affiché tel quel (échappé par React), jamais interprété */}
          <div className="whitespace-pre-wrap rounded-xl border border-border bg-muted/30 px-4 py-3 text-sm">
            {r.reponse?.trim() ? r.reponse : "Aucune réponse."}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label htmlFor={`note-manuelle-${r.reponseId}`} className="text-sm font-medium">Note :</label>
            <Input
              id={`note-manuelle-${r.reponseId}`}
              type="number"
              min={0}
              max={r.pointsMax}
              step={0.5}
              value={notes[r.reponseId] ?? ""}
              onChange={(e) => {
                setNotes((n) => ({ ...n, [r.reponseId]: e.target.value }))
                setErreurs((er) => ({ ...er, [r.reponseId]: undefined }))
              }}
              aria-invalid={!!erreurs[r.reponseId]}
              className="w-24 rounded-lg"
            />
            <span className="text-sm text-muted-foreground">/ {r.pointsMax}</span>
            <Button className="ml-auto rounded-xl" disabled={noter.isPending} onClick={() => valider(r)}
                    aria-label={`Enregistrer la note : ${r.etudiant}, ${r.question.slice(0, 40)}`}>
              Enregistrer la note
            </Button>
            {erreurs[r.reponseId] && <p className="w-full text-xs text-destructive">{erreurs[r.reponseId]}</p>}
          </div>
        </div>
      ))}
    </section>
  )
}
