import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { cleResultats, libelleEtatCopie, resultatsEvaluation } from "@/lib/statistiquesEnseignant"
import { messageErreur } from "@/lib/passation"

// Notes par étudiant d'une évaluation terminée (DS-09, section C) : mêmes lignes que l'export CSV, sans e-mail.
// Une note absente s'affiche « — », jamais 0. Chaque copie remise mène à la copie complète (relecture, révision).

export default function NotesParEtudiant({ evaluationId }: { evaluationId: number }) {
  const requete = useQuery({ queryKey: cleResultats(evaluationId), queryFn: () => resultatsEvaluation(evaluationId) })

  if (requete.isError) {
    return <p role="alert" className="text-sm text-destructive">{messageErreur(requete.error, "Impossible de charger les notes.")}</p>
  }
  if (!requete.data) return <p className="text-sm text-muted-foreground">Chargement des notes…</p>

  const { lignes, bareme } = requete.data
  if (lignes.length === 0) {
    return <p className="text-sm text-muted-foreground">Aucun étudiant n'est inscrit dans les promotions de cette évaluation.</p>
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-border">
      <table className="w-full border-collapse text-sm">
        <caption className="sr-only">Notes par étudiant</caption>
        <thead>
          <tr className="border-b border-border bg-muted/30 text-left text-[11px] uppercase tracking-wider text-muted-foreground">
            <th scope="col" className="px-3 py-2">Étudiant</th>
            <th scope="col" className="px-3 py-2">État</th>
            <th scope="col" className="px-3 py-2 text-right">Note / {bareme}</th>
            <th scope="col" className="px-3 py-2 text-right">Sur 20</th>
            <th scope="col" className="px-3 py-2"><span className="sr-only">Copie</span></th>
          </tr>
        </thead>
        <tbody>
          {lignes.map((l) => (
            <tr key={l.etudiantId} className="border-b border-border last:border-none">
              <td className="px-3 py-2 font-medium">
                {l.prenom} {l.nom}
                {l.numEtudiant && <span className="block text-[11px] font-normal text-muted-foreground">{l.numEtudiant}</span>}
              </td>
              <td className="px-3 py-2 text-muted-foreground">{libelleEtatCopie(l.etat)}</td>
              <td className="px-3 py-2 text-right">{l.note === null ? "—" : l.note.toLocaleString("fr-FR")}</td>
              <td className="px-3 py-2 text-right font-semibold">{l.noteSurVingt === null ? "—" : l.noteSurVingt.toLocaleString("fr-FR")}</td>
              <td className="px-3 py-2 text-right">
                {l.soumissionId !== null && (
                  <Link to={`/enseignant/copies/${l.soumissionId}`} aria-label={`Voir la copie de ${l.prenom} ${l.nom}`}
                        className="text-sm font-medium text-primary hover:underline">
                    Voir la copie
                  </Link>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
