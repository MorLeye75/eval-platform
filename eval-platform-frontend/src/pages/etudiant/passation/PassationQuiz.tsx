import { useState, useEffect, useRef } from "react"
import { useParams, useNavigate, Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Checkbox } from "@/components/ui/checkbox"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { getEvaluation } from "@/mocks/evaluations"
import { useExamTimer } from "@/hooks/useExamTimer"
import { useAntiCheat } from "@/hooks/useAntiCheat"
import {
  Clock,
  Timer,
  ChevronRight,
  Send,
  Eye,
  Zap,
  AlertTriangle,
} from "lucide-react"

export default function PassationQuiz() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const evaluation = getEvaluation(id)
  const questions = evaluation?.questions ?? []
  const dureeQuestion = evaluation?.dureeQuestion ?? 30

  const [demarre, setDemarre] = useState(false)
  const [indexCourant, setIndexCourant] = useState(0)
  const [reponses, setReponses] = useState<Record<number, number[]>>({})
  const [tempsQuestion, setTempsQuestion] = useState(dureeQuestion)
  // Heure de fin de la question courante (le temps affiché en est dérivé : pas de dérive)
  const [finQuestionA, setFinQuestionA] = useState<number | null>(null)

  const { changementsOnglet } = useAntiCheat({ enabled: demarre })

  const { tempsRestantFormate: tempsGlobalFormate } = useExamTimer({
    dureeSecondes: (evaluation?.duree ?? 5) * 60,
    onExpire: () => navigate(`/examen/${id}/expiration`, { replace: true }),
    enabled: demarre && !!evaluation,
  })

  const question = questions[indexCourant]
  const estDerniere = indexCourant === questions.length - 1

  // ---------- Passage à la question suivante (ou fin) ----------
  // TODO : POST /api/soumissions/{id}/reponses (sauvegarde de la question)
  function questionSuivante() {
    if (estDerniere) {
      const nbTraitees = Object.values(reponses).filter((r) => r.length > 0).length
      navigate(`/examen/${id}/confirmation`, {
        replace: true,
        state: {
          titre: evaluation?.titre ?? "Quiz chronométré",
          traitees: nbTraitees,
          total: questions.length,
        },
      })
    } else {
      setIndexCourant((i) => i + 1)
      setTempsQuestion(dureeQuestion)
      setFinQuestionA(Date.now() + dureeQuestion * 1000)
    }
  }

  // La fonction est relue via une ref : les effets ci-dessous ne dépendent pas
  // des réponses et le chrono n'est pas recréé à chaque clic.
  const questionSuivanteRef = useRef(questionSuivante)
  useEffect(() => {
    questionSuivanteRef.current = questionSuivante
  })

  // ---------- Chrono par question : décompte depuis l'heure de fin ----------
  useEffect(() => {
    if (!demarre || finQuestionA === null) return
    const tick = () =>
      setTempsQuestion(Math.max(0, Math.round((finQuestionA - Date.now()) / 1000)))
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [demarre, finQuestionA])

  // Temps écoulé → question suivante (effet séparé : la mise à jour d'état reste pure)
  useEffect(() => {
    if (demarre && finQuestionA !== null && tempsQuestion === 0) {
      questionSuivanteRef.current()
    }
  }, [demarre, finQuestionA, tempsQuestion])

  // ---------- Cas d'erreur ----------
  if (!evaluation || questions.length === 0) {
    return (
      <div className="mx-auto flex max-w-2xl flex-col items-center gap-4 py-10 text-center">
        <Card className="w-full rounded-2xl border-border/60 shadow-sm">
          <CardContent className="py-10 text-sm text-muted-foreground">
            Cette évaluation est introuvable ou ne contient aucune question.
          </CardContent>
        </Card>
        <Button
          variant="outline"
          render={<Link to="/etudiant/evaluations">Retour aux évaluations</Link>}
        />
      </div>
    )
  }

  // ---------- Écran de démarrage ----------
  if (!demarre) {
    return (
      <div className="mx-auto flex max-w-lg flex-col items-center gap-6 py-8 text-center">
        <div className="flex size-16 items-center justify-center rounded-full bg-primary/10">
          <Zap className="size-8 text-primary" />
        </div>

        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-extrabold tracking-tight">
            {evaluation.titre}
          </h1>
          <p className="text-muted-foreground">{evaluation.description}</p>
        </div>

        <Card className="w-full rounded-2xl border-amber-500/30 bg-amber-500/10 shadow-sm">
          <CardContent className="flex flex-col gap-3 py-5 text-left text-sm text-amber-700 dark:text-amber-400">
            <div className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <span className="font-semibold">
                Ce quiz fonctionne différemment d'un QCM classique
              </span>
            </div>
            <ul className="flex flex-col gap-1.5 pl-6 text-xs">
              <li>
                Chaque question dispose de {dureeQuestion} secondes.
              </li>
              <li>
                À la fin du temps, la question suivante s'affiche automatiquement.
              </li>
              <li>
                Vous ne pouvez pas revenir sur une question déjà passée.
              </li>
              <li>
                {questions.length} questions · durée totale {evaluation.duree} min.
              </li>
            </ul>
          </CardContent>
        </Card>

        <Button size="lg" className="w-full shadow-md" onClick={() => {
            setDemarre(true)
            setFinQuestionA(Date.now() + dureeQuestion * 1000)
          }}>
          <Zap className="mr-1 size-4" />
          Commencer le quiz
        </Button>
      </div>
    )
  }

  // ---------- Gestion des réponses ----------
  function selectionUnique(choixId: number) {
    setReponses((r) => ({ ...r, [question.id]: [choixId] }))
  }

  function selectionMultiple(choixId: number, coche: boolean) {
    setReponses((r) => {
      const actuelles = r[question.id] ?? []
      return {
        ...r,
        [question.id]: coche
          ? [...actuelles, choixId]
          : actuelles.filter((c) => c !== choixId),
      }
    })
  }

  const pourcentageQuestion = (tempsQuestion / dureeQuestion) * 100
  const questionCritique = tempsQuestion <= 5
  const progression = ((indexCourant + 1) / questions.length) * 100

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <p className="text-sm font-semibold text-muted-foreground">
        {evaluation.titre}
      </p>

      {/* Chrono de la question — l'élément central */}
      <Card
        className={`rounded-2xl shadow-sm transition-colors ${
          questionCritique
            ? "border-destructive/40 bg-destructive/5"
            : "border-border/60"
        }`}
      >
        <CardContent className="flex flex-col gap-3 py-4">
          <div className="flex items-center justify-between gap-4">
            <span
              className={`flex items-center gap-2 text-3xl font-extrabold tabular-nums ${
                questionCritique ? "text-destructive" : "text-primary"
              }`}
            >
              <Timer className="size-6" />
              {tempsQuestion}s
            </span>

            <div className="flex flex-col items-end gap-1 text-xs text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <Clock className="size-3.5" />
                Total : {tempsGlobalFormate}
              </span>
              <span>
                Question {indexCourant + 1} / {questions.length}
              </span>
            </div>
          </div>

          {/* Barre du temps restant sur la question */}
          <div className="h-2 overflow-hidden rounded-full bg-muted">
            <div
              className={`h-full rounded-full transition-all duration-1000 ease-linear ${
                questionCritique ? "bg-destructive" : "bg-primary"
              }`}
              style={{ width: `${pourcentageQuestion}%` }}
            />
          </div>
        </CardContent>
      </Card>

      {/* Alerte anti-triche */}
      {changementsOnglet > 0 && (
        <div className="flex items-center gap-2 rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          <Eye className="size-4 shrink-0" />
          <span>
            {changementsOnglet} sortie{changementsOnglet > 1 ? "s" : ""} de la
            page détectée{changementsOnglet > 1 ? "s" : ""} et enregistrée
            {changementsOnglet > 1 ? "s" : ""}.
          </span>
        </div>
      )}

      {/* Progression globale */}
      <Progress value={progression} className="h-2" />

      {/* Question courante */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <CardTitle className="text-base font-bold">
            Question {indexCourant + 1}
          </CardTitle>
          <Badge
            variant="outline"
            className="rounded-full border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary"
          >
            {question.points} pts
          </Badge>
        </CardHeader>

        <CardContent className="flex flex-col gap-5">
          <p className="text-base font-medium">{question.enonce}</p>

          {question.estChoixMultiple ? (
            <div className="flex flex-col gap-3">
              <p className="text-xs text-muted-foreground">
                Plusieurs réponses possibles
              </p>
              {(question.choix ?? []).map((choix) => (
                <label
                  key={choix.id}
                  className="flex cursor-pointer items-center gap-3 rounded-xl border border-border/60 p-3.5 transition-colors hover:bg-muted/50"
                >
                  <Checkbox
                    checked={(reponses[question.id] ?? []).includes(choix.id)}
                    onCheckedChange={(coche) =>
                      selectionMultiple(choix.id, coche === true)
                    }
                  />
                  <span className="text-sm">{choix.texte}</span>
                </label>
              ))}
            </div>
          ) : (
            <RadioGroup
              value={String((reponses[question.id] ?? [])[0] ?? "")}
              onValueChange={(v) => selectionUnique(Number(v))}
              className="flex flex-col gap-3"
            >
              {(question.choix ?? []).map((choix) => (
                <label
                  key={choix.id}
                  className="flex cursor-pointer items-center gap-3 rounded-xl border border-border/60 p-3.5 transition-colors hover:bg-muted/50"
                >
                  <RadioGroupItem value={String(choix.id)} id={`q-${choix.id}`} />
                  <span className="text-sm">{choix.texte}</span>
                </label>
              ))}
            </RadioGroup>
          )}
        </CardContent>
      </Card>

      {/* Une seule action : avancer. Pas de retour possible. */}
      <div className="flex flex-col items-center gap-2">
        <Button size="lg" className="w-full shadow-md" onClick={questionSuivante}>
          {estDerniere ? (
            <>
              <Send className="mr-1 size-4" />
              Terminer le quiz
            </>
          ) : (
            <>
              Question suivante
              <ChevronRight className="ml-1 size-4" />
            </>
          )}
        </Button>
        <p className="text-xs text-muted-foreground">
          Validation définitive — aucun retour en arrière possible.
        </p>
      </div>
    </div>
  )
}
