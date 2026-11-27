import { useState } from "react"
import { Link, useParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, History, Lock, PenLine } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  LIBELLE_VERDICT,
  cleCopie,
  lireCopie,
  reviserNote,
  type CopieEnseignantApi,
  type ReponseCopieApi,
} from "@/lib/copiesEnseignant"
import { lireNoteSaisie, noterReponse } from "@/lib/correctionsIA"
import { cleResultats } from "@/lib/statistiquesEnseignant"
import { messageErreur } from "@/lib/passation"

// Copie complète d'un étudiant pour son enseignant : réponses, code, verdicts des cas de test, notes. Avant la
// publication des résultats, une note déjà posée (QCM, Judge0, IA, à la main) peut être revue avec un motif ; chaque
// révision est tracée et le motif est montré à l'étudiant. Une réponse pas encore notée (code en repli manuel) se note ici.

const nombre = (n: number) => n.toLocaleString("fr-FR")
const date = (iso: string) => new Date(iso).toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" })

export default function CopieEnseignant() {
  const { id } = useParams()
  const soumissionId = Number(id)
  const requete = useQuery({ queryKey: cleCopie(soumissionId), queryFn: () => lireCopie(soumissionId) })

  if (requete.isError) {
    return <p role="alert" className="text-sm text-destructive">{messageErreur(requete.error, "Impossible de charger la copie.")}</p>
  }
  if (!requete.data) return <p className="text-sm text-muted-foreground">Chargement de la copie…</p>
  const c = requete.data

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-5">
      <Link to="/enseignant/publication" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden /> Publication des résultats
      </Link>

      <header className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[11px] uppercase tracking-wider text-muted-foreground">{c.evaluation}</p>
          <h1 className="text-xl font-bold break-words">
            Copie de {c.etudiant}
            {c.numEtudiant && <span className="ml-2 text-sm font-normal text-muted-foreground">{c.numEtudiant}</span>}
          </h1>
        </div>
        <p className="text-lg font-semibold">
          {c.noteFinale === null ? <span className="text-muted-foreground">Note finale à venir</span>
            : `${nombre(c.noteFinale)} / ${nombre(c.bareme)}`}
        </p>
      </header>

      {c.resultatsPublies ? (
        <p className="flex items-center gap-2 rounded-xl bg-muted px-4 py-2.5 text-sm">
          <Lock className="size-4 shrink-0" aria-hidden />
          Les résultats sont publiés : les notes ne sont plus modifiables.
        </p>
      ) : c.notesModifiables && (
        <p className="rounded-xl bg-primary/5 px-4 py-2.5 text-sm">
          Avant la publication, vous pouvez revoir chaque note. Le motif est obligatoire, conservé dans l'historique et
          montré à l'étudiant avec sa note.
        </p>
      )}

      {c.reponses.map((r) => <Question key={`${r.ordre}-${r.reponseId}`} copie={c} r={r} />)}
    </div>
  )
}

function Question({ copie, r }: { copie: CopieEnseignantApi; r: ReponseCopieApi }) {
  const idTitre = `question-${r.ordre}-${r.reponseId ?? "vide"}`
  return (
    <article aria-labelledby={idTitre} className="flex flex-col gap-3 rounded-2xl border border-border bg-card p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 id={idTitre} className="min-w-0 font-semibold break-words">
          <span className="text-muted-foreground">Question {r.ordre} · </span>{r.enonce}
        </h2>
        {r.reponseId === null ? (
          // Rien d'enregistré (quiz laissé vide) : compte 0, rien à noter
          <span className="rounded-full bg-muted px-2.5 py-0.5 text-sm font-semibold text-muted-foreground">Sans réponse</span>
        ) : (
          <span className={`rounded-full px-2.5 py-0.5 text-sm font-semibold ${r.notee ? "bg-primary/10 text-primary" : "bg-amber-100 text-amber-800"}`}>
            {r.notee && r.note !== null ? `${nombre(r.note)} / ${nombre(r.pointsMax)}` : "À noter"}
          </span>
        )}
      </div>

      <Contenu r={r} />
      {r.commentaire && <p className="text-sm text-muted-foreground">{r.commentaire}</p>}
      {r.revisions.length > 0 && <Historique r={r} />}

      {r.reponseId !== null && copie.notesModifiables && (
        r.notee ? <Revision copie={copie} r={r} reponseId={r.reponseId} />
          : <NoteManuelle copie={copie} r={r} reponseId={r.reponseId} />
      )}
    </article>
  )
}

function Contenu({ r }: { r: ReponseCopieApi }) {
  if (r.reponseId === null) return <p className="text-sm text-muted-foreground">Aucune réponse enregistrée.</p>
  if (r.type === "CODE") {
    return (
      <div className="flex flex-col gap-3">
        {/* Code de l'étudiant : affiché tel quel (échappé par React), jamais exécuté ici */}
        <pre className="max-h-96 overflow-auto rounded-xl border border-border bg-muted/40 p-3 text-xs">
          <code>{r.code?.trim() ? r.code : "Aucun code soumis."}</code>
        </pre>
        {r.langage && <p className="text-[11px] text-muted-foreground">Langage : {r.langage}</p>}
        {r.casDeTests.length > 0 ? (
          <div className="overflow-x-auto rounded-xl border border-border">
            <table className="w-full border-collapse text-xs">
              <caption className="sr-only">Cas de test</caption>
              <thead>
                <tr className="border-b border-border bg-muted/30 text-left text-[11px] uppercase tracking-wider text-muted-foreground">
                  <th scope="col" className="px-3 py-2">Entrée</th>
                  <th scope="col" className="px-3 py-2">Attendu</th>
                  <th scope="col" className="px-3 py-2">Obtenu</th>
                  <th scope="col" className="px-3 py-2">Verdict</th>
                </tr>
              </thead>
              <tbody>
                {r.casDeTests.map((cas, i) => (
                  <tr key={i} className="border-b border-border align-top last:border-none">
                    <td className="px-3 py-2 font-mono whitespace-pre-wrap">
                      {cas.entree}
                      {cas.masque && <span className="block font-sans text-[10px] text-muted-foreground">(masqué pour l'étudiant)</span>}
                    </td>
                    {/* Espaces rendus visibles : ce sont eux qui font souvent échouer un cas */}
                    <td className="px-3 py-2 font-mono whitespace-pre-wrap">{visible(cas.sortieAttendue)}</td>
                    <td className="px-3 py-2 font-mono whitespace-pre-wrap">{visible(cas.sortieObtenue)}</td>
                    <td className={`px-3 py-2 font-semibold ${cas.reussi ? "text-primary" : "text-destructive"}`}>
                      {cas.verdict ? LIBELLE_VERDICT[cas.verdict] : cas.reussi ? "Réussi" : "Échec"}
                      {cas.messageErreur && <span className="block font-normal text-muted-foreground">{cas.messageErreur}</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : !r.notee && (
          <p className="text-sm text-muted-foreground">Pas d'exécution automatique : Judge0 indisponible ou aucun cas de test.</p>
        )}
      </div>
    )
  }
  if (r.choix.length > 0) {
    return (
      <ul className="flex flex-col gap-1 text-sm">
        {r.choix.map((ch, i) => (
          <li key={i} className={ch.correct ? "font-semibold" : undefined}>
            {ch.coche ? "☑" : "☐"} {ch.texte}{ch.correct && <span className="ml-1 text-[11px] text-primary">(bonne réponse)</span>}
          </li>
        ))}
      </ul>
    )
  }
  return (
    <div className="whitespace-pre-wrap rounded-xl border border-border bg-muted/30 px-4 py-3 text-sm">
      {r.texte?.trim() ? r.texte : "Aucune réponse."}
    </div>
  )
}

/** Rend visibles les espaces de fin de ligne (« b␠ » au lieu de « b »). */
function visible(sortie: string | null) {
  if (sortie === null) return "—"
  return sortie.replace(/ +$/gm, (espaces) => "␠".repeat(espaces.length))
}

function Historique({ r }: { r: ReponseCopieApi }) {
  return (
    <div className="flex flex-col gap-1">
      <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
        <History className="size-3.5" aria-hidden /> Historique
      </p>
      <ul aria-label="Historique des révisions" className="flex flex-col gap-1 text-xs">
        {r.revisions.map((rv, i) => (
          <li key={i} className="rounded-lg bg-muted/40 px-3 py-2">
            <span className="font-semibold">{nombre(rv.ancienneNote)} → {nombre(rv.nouvelleNote)}</span>
            {" · "}{rv.motif}
            <span className="block text-muted-foreground">{rv.auteur}, le {date(rv.reviseeLe)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function useRafraichir(copie: CopieEnseignantApi) {
  const client = useQueryClient()
  return () => {
    void client.invalidateQueries({ queryKey: cleCopie(copie.soumissionId) })
    void client.invalidateQueries({ queryKey: cleResultats(copie.evaluationId) })
  }
}

function Revision({ copie, r, reponseId }: { copie: CopieEnseignantApi; r: ReponseCopieApi; reponseId: number }) {
  const [ouvert, setOuvert] = useState(false)
  const [note, setNote] = useState("")
  const [motif, setMotif] = useState("")
  const [erreur, setErreur] = useState<string | null>(null)
  const rafraichir = useRafraichir(copie)
  const reviser = useMutation({
    mutationFn: (v: { note: number; motif: string }) => reviserNote(reponseId, v.note, v.motif),
    onSuccess: () => { setOuvert(false); setNote(""); setMotif(""); setErreur(null) },
    onError: (e) => setErreur(messageErreur(e, "La révision n'a pas pu être enregistrée.")),
    onSettled: rafraichir,
  })

  if (!ouvert) {
    return (
      <Button variant="outline" className="self-start rounded-xl" onClick={() => setOuvert(true)}
              aria-label={`Réviser la note : question ${r.ordre}`}>
        <PenLine className="size-4" aria-hidden /> Réviser la note
      </Button>
    )
  }

  function enregistrer() {
    const valeur = lireNoteSaisie(note, r.pointsMax)
    if (valeur === null) { setErreur(`Saisissez une note comprise entre 0 et ${r.pointsMax}.`); return }
    if (!motif.trim()) { setErreur("Le motif est obligatoire : il est conservé et montré à l'étudiant."); return }
    reviser.mutate({ note: valeur, motif: motif.trim() })
  }

  const base = `revision-${reponseId}`
  return (
    <div className="flex flex-col gap-2 rounded-xl border border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor={`${base}-note`} className="text-sm font-medium">Nouvelle note</label>
        <Input id={`${base}-note`} type="number" min={0} max={r.pointsMax} step={0.5} value={note}
               onChange={(e) => { setNote(e.target.value); setErreur(null) }} className="w-24 rounded-lg" />
        <span className="text-sm text-muted-foreground">/ {nombre(r.pointsMax)}</span>
      </div>
      <label htmlFor={`${base}-motif`} className="text-sm font-medium">Motif (montré à l'étudiant)</label>
      <textarea id={`${base}-motif`} value={motif} maxLength={500} rows={2}
                onChange={(e) => { setMotif(e.target.value); setErreur(null) }}
                className="rounded-lg border border-input bg-background px-3 py-2 text-sm" />
      {erreur && <p role="alert" className="text-xs text-destructive">{erreur}</p>}
      <div className="flex flex-wrap gap-2">
        <Button className="rounded-xl" disabled={reviser.isPending} onClick={enregistrer}>Enregistrer la révision</Button>
        <Button variant="ghost" className="rounded-xl" onClick={() => { setOuvert(false); setErreur(null) }}>Annuler</Button>
      </div>
    </div>
  )
}

function NoteManuelle({ copie, r, reponseId }: { copie: CopieEnseignantApi; r: ReponseCopieApi; reponseId: number }) {
  const [note, setNote] = useState("")
  const [erreur, setErreur] = useState<string | null>(null)
  const rafraichir = useRafraichir(copie)
  const noter = useMutation({
    mutationFn: (valeur: number) => noterReponse(reponseId, valeur),
    onSuccess: () => { setNote(""); setErreur(null) },
    onError: (e) => setErreur(messageErreur(e, "La note n'a pas pu être enregistrée.")),
    onSettled: rafraichir,
  })

  function enregistrer() {
    const valeur = lireNoteSaisie(note, r.pointsMax)
    if (valeur === null) { setErreur(`Saisissez une note comprise entre 0 et ${r.pointsMax}.`); return }
    noter.mutate(valeur)
  }

  const idNote = `note-${reponseId}`
  return (
    <div className="flex flex-wrap items-center gap-2">
      <label htmlFor={idNote} className="text-sm font-medium">Note</label>
      <Input id={idNote} type="number" min={0} max={r.pointsMax} step={0.5} value={note}
             onChange={(e) => { setNote(e.target.value); setErreur(null) }} className="w-24 rounded-lg" />
      <span className="text-sm text-muted-foreground">/ {nombre(r.pointsMax)}</span>
      <Button className="rounded-xl" disabled={noter.isPending} onClick={enregistrer}>Enregistrer la note</Button>
      {erreur && <p role="alert" className="w-full text-xs text-destructive">{erreur}</p>}
    </div>
  )
}
