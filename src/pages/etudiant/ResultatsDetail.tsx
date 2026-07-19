import { useParams, Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { getEvaluation } from "@/mocks/evaluations"
import {
  ArrowLeft,
  CircleCheck,
  CircleX,
  MessageSquare,
  Award,
  Hourglass,
  ListChecks,
} from "lucide-react"

// Formatage à la française (virgule décimale, décimales inutiles supprimées)
function formatNote(note: number) {
  return note.toLocaleString("fr-FR", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })
}

function ChoixLigne({
  texte,
  estCorrect,
  choisiParEtudiant,
}: {
  texte: string
  estCorrect: boolean
  choisiParEtudiant: boolean
}) {
  const correctChoisi = estCorrect && choisiParEtudiant
  const errone = !estCorrect && choisiParEtudiant
  const manque = estCorrect && !choisiParEtudiant

  let style = "border-border/60"
  if (correctChoisi) style = "border-primary/40 bg-primary/5"
  if (errone) style = "border-destructive/40 bg-destructive/5"
  if (manque) style = "border-dashed border-primary/30 bg-primary/5"

  return (
    <div className={`flex items-center gap-3 rounded-xl border p-3 ${style}`}>
      {correctChoisi && <CircleCheck className="size-4 shrink-0 text-primary" />}
      {errone && <CircleX className="size-4 shrink-0 text-destructive" />}
      {manque && <CircleCheck className="size-4 shrink-0 text-primary/50" />}
      {!estCorrect && !choisiParEtudiant && <span className="size-4 shrink-0" />}

      <span className="text-sm">{texte}</span>

      {manque && (
        <span className="ml-auto text-xs text-muted-foreground">
          Réponse attendue
        </span>
      )}
    </div>
  )
}

export default function ResultatsDetail() {
  const { id } = useParams<{ id: string }>()
  const evaluation = getEvaluation(id)

  const boutonRetour = (
    <Button
      variant="ghost"
      size="sm"
      className="self-start text-muted-foreground"
      render={
        <Link to="/etudiant/evaluations">
          <ArrowLeft className="mr-1 size-4" />
          Retour aux évaluations
        </Link>
      }
    />
  )

  // ---------- Cas 1 : évaluation introuvable ou pas encore passée ----------
  if (!evaluation || !evaluation.resultat) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4">
        {boutonRetour}
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            Aucun résultat trouvé pour cette évaluation.
          </CardContent>
        </Card>
      </div>
    )
  }

  const r = evaluation.resultat

  // ---------- Cas 2 : résultats pas encore publiés par l'enseignant ----------
  if (!r.resultatsPublies) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-5">
        {boutonRetour}

        <div className="flex flex-col items-center gap-5 py-8 text-center">
          <div className="flex size-16 items-center justify-center rounded-full bg-muted">
            <Hourglass className="size-8 text-muted-foreground" />
          </div>

          <div className="flex flex-col gap-2">
            <h1 className="text-2xl font-extrabold tracking-tight">
              Résultats en attente de publication
            </h1>
            <p className="max-w-md text-muted-foreground">
              Votre copie a bien été enregistrée. Les résultats seront visibles
              dès que votre enseignant les aura publiés. Vous recevrez une
              notification à ce moment-là.
            </p>
          </div>

          <Card className="w-full max-w-md rounded-2xl border-border/60 shadow-sm">
            <CardContent className="flex flex-col gap-3 py-5 text-left text-sm">
              <div className="flex items-center justify-between gap-4">
                <span className="text-muted-foreground">Évaluation</span>
                <span className="font-semibold">{evaluation.titre}</span>
              </div>
              <div className="flex items-center justify-between gap-4">
                <span className="text-muted-foreground">Soumise le</span>
                <span className="font-semibold">{r.dateSoumission}</span>
              </div>
              <div className="flex items-center justify-between gap-4">
                <span className="text-muted-foreground">Statut</span>
                <Badge
                  variant="outline"
                  className="rounded-full border-amber-500/20 bg-amber-500/10 px-3 py-1 text-xs font-semibold text-amber-700 dark:text-amber-400"
                >
                  En correction
                </Badge>
              </div>
            </CardContent>
          </Card>

          <Button
            variant="outline"
            render={
              <Link to="/etudiant/evaluations">
                <ListChecks className="mr-1 size-4" />
                Mes évaluations
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  // ---------- Cas 3 : résultats publiés — affichage complet ----------
  const pourcentage = (r.noteFinale / evaluation.noteStandard) * 100

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      {boutonRetour}

      {/* En-tête + note */}
      <Card className="rounded-2xl border-primary/15 bg-gradient-to-br from-primary/[0.06] to-primary/[0.02] shadow-sm">
        <CardContent className="flex flex-col gap-4 py-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm text-muted-foreground">Résultat</p>
              <h1 className="text-2xl font-extrabold tracking-tight">
                {evaluation.titre}
              </h1>
            </div>
            <div className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/10">
              <Award className="size-6 text-primary" />
            </div>
          </div>

          {/* Note finale sur le barème défini par l'enseignant */}
          <div className="flex flex-wrap items-end gap-x-3 gap-y-1">
            <span className="text-4xl font-extrabold text-primary">
              {formatNote(r.noteFinale)}
            </span>
            <span className="pb-1 text-lg text-muted-foreground">
              / {evaluation.noteStandard}
            </span>
            <span className="pb-1.5 text-sm text-muted-foreground">
              ({r.pointsObtenus} sur {r.pointsMax} points)
            </span>
          </div>

          <Progress value={pourcentage} className="h-2" />

          <p className="text-sm text-muted-foreground">
            Moyenne de la promotion : {formatNote(r.moyennePromotion)} /{" "}
            {evaluation.noteStandard}
          </p>
        </CardContent>
      </Card>

      {/* Feedback général */}
      {r.feedbackGeneral && (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center gap-2 pb-3">
            <MessageSquare className="size-4 text-primary" />
            <CardTitle className="text-base font-bold">
              Commentaire de l'enseignant
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">{r.feedbackGeneral}</p>
          </CardContent>
        </Card>
      )}

      {/* Détail par question */}
      <div className="flex flex-col gap-4">
        <h2 className="text-lg font-bold">Détail des réponses</h2>

        {evaluation.questions.map((q, index) => {
          const reponsesEtudiant = r.reponsesEtudiant[q.id] ?? []

          // Calcul des points obtenus : tout juste = tous les points, sinon 0
          const bonnesReponses = q.choix
            .filter((c) => c.estCorrect)
            .map((c) => c.id)
          const toutJuste =
            bonnesReponses.length === reponsesEtudiant.length &&
            bonnesReponses.every((b) => reponsesEtudiant.includes(b))
          const pointsObtenus = toutJuste ? q.points : 0

          const reussie = pointsObtenus === q.points
          const commentaire = r.commentaires[q.id]

          return (
            <Card key={q.id} className="rounded-2xl border-border/60 shadow-sm">
              <CardHeader className="flex flex-row items-start justify-between gap-4 pb-3">
                <CardTitle className="text-base font-bold">
                  Question {index + 1}
                </CardTitle>
                <Badge
                  variant="outline"
                  className={`rounded-full px-3 py-1 text-xs font-semibold ${
                    reussie
                      ? "border-primary/20 bg-primary/10 text-primary"
                      : "border-destructive/20 bg-destructive/10 text-destructive"
                  }`}
                >
                  {pointsObtenus} / {q.points} pts
                </Badge>
              </CardHeader>

              <CardContent className="flex flex-col gap-4">
                <p className="text-sm font-medium">{q.enonce}</p>

                <div className="flex flex-col gap-2">
                  {q.choix.map((choix) => (
                    <ChoixLigne
                      key={choix.id}
                      texte={choix.texte}
                      estCorrect={choix.estCorrect}
                      choisiParEtudiant={reponsesEtudiant.includes(choix.id)}
                    />
                  ))}
                </div>

                {commentaire && (
                  <div className="flex items-start gap-2 rounded-xl bg-muted/60 p-3 text-sm text-muted-foreground">
                    <MessageSquare className="mt-0.5 size-4 shrink-0" />
                    <span>{commentaire}</span>
                  </div>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}