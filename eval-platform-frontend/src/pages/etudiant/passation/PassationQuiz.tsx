import { useEffect, useRef, useState } from "react"
import { useParams } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Checkbox } from "@/components/ui/checkbox"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { usePassation } from "@/hooks/usePassation"
import { useExamTimer } from "@/hooks/useExamTimer"
import { estCopieVerrouillee, messageErreur, questionQuizSuivante } from "@/lib/passation"
import EtatPassation from "./EtatPassation"
import type { QuestionExamen } from "@/types"
import { Clock, Timer, ChevronRight, Send, Eye } from "lucide-react"

type ReponsesQuiz = Record<number, number[]> // questionId -> choixIds cochés (question en cours seulement)

function depuisCopie(questions: QuestionExamen[]): ReponsesQuiz {
  return Object.fromEntries(questions.map((q) => [q.id, q.reponseActuelle?.choixIds ?? []]))
}

function versReponses(reponses: ReponsesQuiz) {
  return Object.entries(reponses).map(([questionId, choixIds]) => ({ questionId: Number(questionId), choixIds }))
}

function compterTraitees(reponses: ReponsesQuiz) {
  return Object.values(reponses).filter((r) => r.length > 0).length
}

/**
 * Chrono de la question en cours, recréé à chaque question (clé = index) : il part du temps restant donné
 * par le serveur et, à zéro, passe à la suivante. Le serveur clôt de toute façon la question à l'heure dite.
 */
function ChronoQuestion({ secondes, duree, onExpire }: { secondes: number; duree: number; onExpire: () => void }) {
  const { tempsRestant } = useExamTimer({ dureeSecondes: secondes, onExpire })
  const critique = tempsRestant <= 5
  return (
    <div className="flex flex-col gap-3">
      <span
        className={`flex items-center gap-2 text-3xl font-extrabold tabular-nums ${
          critique ? "text-destructive" : "text-primary"
        }`}
      >
        <Timer className="size-6" />
        {tempsRestant}s
      </span>
      <div className="h-2 overflow-hidden rounded-full bg-muted">
        <div
          className={`h-full rounded-full transition-all duration-1000 ease-linear ${
            critique ? "bg-destructive" : "bg-primary"
          }`}
          style={{ width: `${(tempsRestant / Math.max(1, duree)) * 100}%` }}
        />
      </div>
    </div>
  )
}

/**
 * Quiz chronométré (règles imposées par le serveur) : le serveur n'envoie que la question en cours, n'enregistre
 * que sa réponse et la clôt à la fin de son temps ; on ne peut ni revenir en arrière, ni répondre en avance.
 */
export default function PassationQuiz() {
  const { id } = useParams<{ id: string }>()
  const passation = usePassation({ evaluationId: id, depuisCopie, versReponses, compterTraitees })
  const {
    copie,
    questions,
    chargement,
    erreur,
    reponses,
    setReponses,
    tempsRestantFormate,
    soumettre,
    soumissionEnCours,
    erreurSoumission,
    changementsOnglet,
    sauvegarderMaintenant,
    remplacerCopie,
  } = passation
  const [avancement, setAvancement] = useState(false)
  const [erreurAvance, setErreurAvance] = useState<string | null>(null)

  const quiz = copie?.quiz ?? null
  const termine = quiz !== null && quiz.indexQuestion >= quiz.nbQuestions

  // Toutes les questions closes (reprise après la fin du quiz) : la copie est remise une seule fois
  const soumettreRef = useRef(soumettre)
  useEffect(() => {
    soumettreRef.current = soumettre
  })
  const remiseLancee = useRef(false)
  useEffect(() => {
    if (termine && !remiseLancee.current) {
      remiseLancee.current = true
      soumettreRef.current("confirmation")
    }
  }, [termine])

  if (chargement) return <EtatPassation message="Chargement de votre copie..." retour={false} />
  if (erreur || !copie) return <EtatPassation message={erreur ?? "Évaluation introuvable."} />
  if (!quiz) return <EtatPassation message="Cette évaluation n'est pas un quiz chronométré." />
  if (termine || questions.length === 0) {
    // Remise automatique en échec (réseau…) : sans ce bouton, l'écran attendait sans fin
    if (erreurSoumission && !soumissionEnCours) {
      return (
        <EtatPassation message={erreurSoumission} retour={false} erreur>
          <Button size="lg" onClick={() => soumettre("confirmation")}>
            <Send className="mr-1 size-4" />
            Réessayer la remise
          </Button>
        </EtatPassation>
      )
    }
    return <EtatPassation message="Quiz terminé : remise de votre copie..." retour={false} />
  }

  const question = questions[0]
  const etat = quiz
  const derniere = etat.indexQuestion >= etat.nbQuestions - 1
  const cochees = reponses[question.id] ?? []

  /**
   * Passe à la question suivante (ou remet la copie après la dernière), réponse en cours envoyée avant.
   * Réponse non enregistrée : on reste sur la question (le serveur refuserait ensuite toute réponse à une question
   * passée). Seul le temps écoulé fait avancer quand même : le serveur refuse alors déjà une réponse hors délai.
   */
  async function avancer(tempsEcoule = false) {
    if (!copie || avancement || soumissionEnCours) return
    if (derniere) {
      soumettre("confirmation") // la remise envoie d'abord la dernière réponse
      return
    }
    setAvancement(true)
    setErreurAvance(null)
    try {
      const sauvegarde = await sauvegarderMaintenant()
      if (sauvegarde === "verrouillee") return // copie figée par le serveur : l'écran d'expiration prend le relais
      if (sauvegarde === "refusee" && !tempsEcoule) {
        setErreurAvance("Votre réponse a été refusée par le serveur : modifiez-la puis réessayez.")
        return
      }
      if ((sauvegarde === "echec" || sauvegarde === "conflit") && !tempsEcoule) {
        setErreurAvance("Votre réponse n'a pas pu être enregistrée. Vérifiez votre connexion puis réessayez.")
        return
      }
      remplacerCopie(await questionQuizSuivante(copie.id, etat.indexQuestion))
    } catch (err) {
      if (estCopieVerrouillee(err)) soumettre("expiration")
      else setErreurAvance(messageErreur(err, "Impossible de passer à la question suivante. Réessayez."))
    } finally {
      setAvancement(false)
    }
  }

  function selectionUnique(choixId: number) {
    setReponses((r) => ({ ...r, [question.id]: [choixId] }))
  }

  function selectionMultiple(choixId: number, coche: boolean) {
    setReponses((r) => {
      const actuelles = r[question.id] ?? []
      return { ...r, [question.id]: coche ? [...actuelles, choixId] : actuelles.filter((c) => c !== choixId) }
    })
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <p className="text-sm font-semibold text-muted-foreground">{copie.titreEvaluation}</p>

      {/* Chrono de la question — l'élément central */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex items-start justify-between gap-4 py-4">
          <div className="min-w-0 flex-1">
            <ChronoQuestion
              key={etat.indexQuestion}
              secondes={etat.tempsRestantQuestionSecondes}
              duree={etat.dureeQuestion ?? etat.tempsRestantQuestionSecondes}
              onExpire={() => avancer(true)}
            />
          </div>
          <div className="flex flex-col items-end gap-1 text-xs text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <Clock className="size-3.5" />
              Total : {tempsRestantFormate}
            </span>
            <span>
              Question {etat.indexQuestion + 1} / {etat.nbQuestions}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Alerte anti-triche */}
      {changementsOnglet > 0 && (
        <div className="flex items-center gap-2 rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          <Eye className="size-4 shrink-0" />
          <span>
            {changementsOnglet} sortie{changementsOnglet > 1 ? "s" : ""} de la page détectée
            {changementsOnglet > 1 ? "s" : ""} et enregistrée{changementsOnglet > 1 ? "s" : ""}.
          </span>
        </div>
      )}

      <Progress value={((etat.indexQuestion + 1) / etat.nbQuestions) * 100} className="h-2" />

      {/* Question en cours */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <CardTitle className="text-base font-bold">Question {etat.indexQuestion + 1}</CardTitle>
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
              <p className="text-xs text-muted-foreground">Plusieurs réponses possibles</p>
              {question.choixPossibles.map((choix) => (
                <label
                  key={choix.id}
                  className="flex cursor-pointer items-center gap-3 rounded-xl border border-border/60 p-3.5 transition-colors hover:bg-muted/50"
                >
                  <Checkbox
                    checked={cochees.includes(choix.id)}
                    onCheckedChange={(coche) => selectionMultiple(choix.id, coche === true)}
                  />
                  <span className="text-sm">{choix.texte}</span>
                </label>
              ))}
            </div>
          ) : (
            <RadioGroup
              value={String(cochees[0] ?? "")}
              onValueChange={(v) => selectionUnique(Number(v))}
              className="flex flex-col gap-3"
            >
              {question.choixPossibles.map((choix) => (
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

      {(erreurAvance || erreurSoumission) && (
        <p className="text-center text-sm text-destructive">{erreurAvance ?? erreurSoumission}</p>
      )}

      {/* Une seule action : avancer. Pas de retour possible. */}
      <div className="flex flex-col items-center gap-2">
        <Button size="lg" className="w-full shadow-md" disabled={avancement || soumissionEnCours} onClick={() => avancer()}>
          {derniere ? (
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
        <p className="text-xs text-muted-foreground">Validation définitive — aucun retour en arrière possible.</p>
      </div>
    </div>
  )
}
