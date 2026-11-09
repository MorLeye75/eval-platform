import type { ReactNode } from "react"
import { Link, Navigate, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import {
  ArrowLeft,
  CircleCheck,
  CircleX,
  EyeOff,
  Info,
  MessageSquare,
  Award,
  Hourglass,
  ListChecks,
} from "lucide-react"
import { messageErreur } from "@/lib/passation"
import { LANGAGES_LABELS } from "@/lib/langages"
import {
  cleResultats,
  formatDate,
  formatEcart,
  formatNote,
  libelleVerdict,
  resultatsCopie,
} from "@/lib/resultats"
import type { CasTestResultat, ChoixResultat, ResultatQuestion, ResultatsCopie } from "@/types"

function ChoixLigne({ choix }: { choix: ChoixResultat }) {
  const { texte, coche, estCorrect } = choix
  // Corrigé pas encore visible : seule la réponse de l'étudiant est montrée
  const corrige = estCorrect !== null
  const correctChoisi = corrige && estCorrect && coche
  const errone = corrige && !estCorrect && coche
  const manque = corrige && estCorrect && !coche

  let style = "border-border/60"
  if (!corrige && coche) style = "border-primary/30 bg-muted/60"
  if (correctChoisi) style = "border-primary/40 bg-primary/5"
  if (errone) style = "border-destructive/40 bg-destructive/5"
  if (manque) style = "border-dashed border-primary/30 bg-primary/5"

  return (
    <div className={`flex items-center gap-3 rounded-xl border p-3 ${style}`}>
      {correctChoisi && <CircleCheck className="size-4 shrink-0 text-primary" />}
      {errone && <CircleX className="size-4 shrink-0 text-destructive" />}
      {manque && <CircleCheck className="size-4 shrink-0 text-primary/50" />}
      {!correctChoisi && !errone && !manque && <span className="size-4 shrink-0" />}

      <span className="text-sm">{texte}</span>

      {coche && !corrige && <span className="ml-auto text-xs text-muted-foreground">Votre réponse</span>}
      {errone && <span className="ml-auto text-xs text-muted-foreground">Votre réponse</span>}
      {manque && <span className="ml-auto text-xs text-muted-foreground">Réponse attendue</span>}
    </div>
  )
}

function BlocTexte({ titre, children }: { titre: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-muted-foreground">{titre}</span>
      <pre className="overflow-x-auto whitespace-pre-wrap rounded-lg bg-muted/60 p-2 font-mono text-xs">{children}</pre>
    </div>
  )
}

function CasTestLigne({ cas, numero }: { cas: CasTestResultat; numero: number }) {
  const temps = cas.tempsExecution !== null ? ` · ${formatNote(cas.tempsExecution)} s` : ""
  return (
    <div
      className={`flex flex-col gap-2 rounded-xl border p-3 ${
        cas.reussi ? "border-primary/30 bg-primary/5" : "border-destructive/30 bg-destructive/5"
      }`}
    >
      <div className="flex items-center gap-2 text-sm">
        {cas.reussi ? (
          <CircleCheck className="size-4 shrink-0 text-primary" />
        ) : (
          <CircleX className="size-4 shrink-0 text-destructive" />
        )}
        <span className="font-medium">
          Cas {numero}
          {cas.masque && " (masqué)"}
        </span>
        <span className="ml-auto text-xs text-muted-foreground">
          {libelleVerdict(cas.verdict, cas.reussi)}
          {temps}
        </span>
      </div>
      {cas.masque ? (
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <EyeOff className="size-3.5" />
          Cas caché par l'enseignant : seul le verdict est communiqué.
        </p>
      ) : (
        <div className="grid gap-2 md:grid-cols-3">
          <BlocTexte titre="Entrée">{cas.entree || "(vide)"}</BlocTexte>
          <BlocTexte titre="Sortie attendue">{cas.sortieAttendue}</BlocTexte>
          <BlocTexte titre="Votre sortie">{cas.sortieObtenue || "(aucune)"}</BlocTexte>
          {cas.message && (
            <div className="md:col-span-3">
              <BlocTexte titre="Message">{cas.message}</BlocTexte>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function QuestionResultat({ q, index, corrigeVisible }: { q: ResultatQuestion; index: number; corrigeVisible: boolean }) {
  // QCM avant le corrigé : le serveur ne donne pas les points (ils révéleraient les bonnes réponses)
  const pointsMasques = !corrigeVisible && (q.type === "QCM" || q.type === "QUIZ_CHRONOMETREE")
  const points = q.noteObtenue ?? 0
  const reussie = q.noteObtenue !== null && q.noteObtenue === q.points

  return (
    <Card className="rounded-2xl border-border/60 shadow-sm">
      <CardHeader className="flex flex-row items-start justify-between gap-4 pb-3">
        <CardTitle className="text-base font-bold">Question {index + 1}</CardTitle>
        <Badge
          variant="outline"
          title={pointsMasques ? "Points affichés avec le corrigé, après la fermeture de l'évaluation" : undefined}
          className={`rounded-full px-3 py-1 text-xs font-semibold ${
            pointsMasques
              ? "border-border/60 bg-muted text-muted-foreground"
              : reussie
                ? "border-primary/20 bg-primary/10 text-primary"
                : "border-destructive/20 bg-destructive/10 text-destructive"
          }`}
        >
          {pointsMasques ? "—" : formatNote(points)} / {formatNote(q.points)} pts
        </Badge>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        <p className="whitespace-pre-wrap text-sm font-medium">{q.enonce}</p>

        {q.choix.length > 0 && (
          <div className="flex flex-col gap-2">
            {q.choix.map((choix) => (
              <ChoixLigne key={choix.id} choix={choix} />
            ))}
          </div>
        )}

        {q.type === "OUVERTE" && (
          <BlocTexte titre="Votre réponse">{q.texteSoumis || "Aucune réponse"}</BlocTexte>
        )}

        {q.type === "CODE" && (
          <>
            <BlocTexte titre={`Votre code${q.langage ? ` (${LANGAGES_LABELS[q.langage] ?? q.langage})` : ""}`}>
              {q.codeSoumis || "Aucun code soumis"}
            </BlocTexte>
            {q.casTests.length > 0 && (
              <div className="flex flex-col gap-2">
                {q.casTests.map((cas, i) => (
                  <CasTestLigne key={i} cas={cas} numero={i + 1} />
                ))}
              </div>
            )}
          </>
        )}

        {q.commentaire && (
          <div className="flex items-start gap-2 rounded-xl bg-muted/60 p-3 text-sm text-muted-foreground">
            <MessageSquare className="mt-0.5 size-4 shrink-0" />
            <span>{q.commentaire}</span>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function EnAttente({ r }: { r: ResultatsCopie }) {
  return (
    <div className="flex flex-col items-center gap-5 py-8 text-center">
      <div className="flex size-16 items-center justify-center rounded-full bg-muted">
        <Hourglass className="size-8 text-muted-foreground" />
      </div>

      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-extrabold tracking-tight">Résultats en attente de publication</h1>
        <p className="max-w-md text-muted-foreground">
          Votre copie a bien été enregistrée. Les résultats seront visibles dès que votre enseignant les aura
          publiés. Vous recevrez une notification à ce moment-là.
        </p>
      </div>

      <Card className="w-full max-w-md rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-col gap-3 py-5 text-left text-sm">
          <div className="flex items-center justify-between gap-4">
            <span className="text-muted-foreground">Évaluation</span>
            <span className="font-semibold">{r.titre}</span>
          </div>
          {r.dateRemise && (
            <div className="flex items-center justify-between gap-4">
              <span className="text-muted-foreground">Remise le</span>
              <span className="font-semibold">{formatDate(r.dateRemise)}</span>
            </div>
          )}
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
  )
}

function Publies({ r }: { r: ResultatsCopie }) {
  const note = r.noteFinale ?? 0
  const total = r.totalPoints ?? 0
  const pourcentage = total > 0 ? (note / total) * 100 : 0

  return (
    <>
      <Card className="rounded-2xl border-primary/15 bg-gradient-to-br from-primary/[0.06] to-primary/[0.02] shadow-sm">
        <CardContent className="flex flex-col gap-4 py-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm text-muted-foreground">Résultat</p>
              <h1 className="text-2xl font-extrabold tracking-tight">{r.titre}</h1>
            </div>
            <div className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/10">
              <Award className="size-6 text-primary" />
            </div>
          </div>

          <div className="flex flex-wrap items-end gap-x-3 gap-y-1">
            <span className="text-4xl font-extrabold text-primary">{formatNote(note)}</span>
            <span className="pb-1 text-lg text-muted-foreground">/ {formatNote(total)}</span>
          </div>

          <Progress value={pourcentage} className="h-2" />

          <p className="text-sm text-muted-foreground">
            {r.moyennePromotion !== null ? (
              <>
                Moyenne de la promotion : {formatNote(r.moyennePromotion)} / {formatNote(total)}
                {r.ecartMoyenne !== null && ` · vous êtes à ${formatEcart(r.ecartMoyenne)} point(s) de la moyenne`}
              </>
            ) : (
              "Moyenne de la promotion communiquée à partir de 5 copies notées."
            )}
          </p>
        </CardContent>
      </Card>

      {!r.corrigeVisible && (
        <div className="flex items-start gap-2 rounded-xl border border-border/60 bg-muted/40 p-3 text-sm text-muted-foreground">
          <Info className="mt-0.5 size-4 shrink-0" />
          <span>
            Les bonnes réponses et les points de chaque question à choix seront affichés après la fermeture de
            l'évaluation, quand plus personne ne compose. Votre note totale est déjà définitive.
          </span>
        </div>
      )}

      {r.feedbackGeneral && (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="flex flex-row items-center gap-2 pb-3">
            <MessageSquare className="size-4 text-primary" />
            <CardTitle className="text-base font-bold">Commentaire de l'enseignant</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="whitespace-pre-wrap text-sm text-muted-foreground">{r.feedbackGeneral}</p>
          </CardContent>
        </Card>
      )}

      <div className="flex flex-col gap-4">
        <h2 className="text-lg font-bold">Détail des réponses</h2>
        {r.questions.map((q, index) => (
          <QuestionResultat key={q.questionId} q={q} index={index} corrigeVisible={r.corrigeVisible} />
        ))}
      </div>
    </>
  )
}

export default function ResultatsDetail() {
  const { id = "" } = useParams<{ id: string }>()
  const { data: r, isPending, error } = useQuery({
    queryKey: cleResultats(id),
    queryFn: () => resultatsCopie(id),
  })

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

  if (isPending) {
    return <p className="py-10 text-center text-sm text-muted-foreground">Chargement des résultats…</p>
  }

  if (error || !r) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4">
        {boutonRetour}
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            {messageErreur(error, "Impossible de charger ces résultats.")}
          </CardContent>
        </Card>
      </div>
    )
  }

  // Copie encore en cours : rien à consulter, retour à l'épreuve
  if (r.etat === "NON_REMISE") {
    return <Navigate to={`/etudiant/evaluations/${r.evaluationId}`} replace />
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      {boutonRetour}
      {r.etat === "EN_ATTENTE" ? <EnAttente r={r} /> : <Publies r={r} />}
    </div>
  )
}
