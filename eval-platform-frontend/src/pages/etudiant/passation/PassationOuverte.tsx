import { useState } from "react"
import { useParams } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Textarea } from "@/components/ui/textarea"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { usePassation } from "@/hooks/usePassation"
import {
  formatNombre,
  messageSansReponse,
  numerosSansReponse,
  TAILLE_MAX_TEXTE,
} from "@/lib/passation"
import { useAntiCheat } from "@/hooks/useAntiCheat"
import EtatPassation from "./EtatPassation"
import type { QuestionExamen } from "@/types"
import {
  Clock,
  ChevronLeft,
  ChevronRight,
  Save,
  Send,
  CircleCheck,
  Eye,
  Info,
} from "lucide-react"

type ReponsesOuvertes = Record<number, string> // questionId -> texte rédigé

function depuisCopie(questions: QuestionExamen[]): ReponsesOuvertes {
  return Object.fromEntries(
    questions.map((q) => [q.id, q.reponseActuelle?.texteSoumis ?? ""])
  )
}

function versReponses(reponses: ReponsesOuvertes) {
  return Object.entries(reponses).map(([questionId, texteSoumis]) => ({
    questionId: Number(questionId),
    texteSoumis,
  }))
}

function compterTraitees(reponses: ReponsesOuvertes) {
  return Object.values(reponses).filter((r) => r.trim().length > 0).length
}

export default function PassationOuverte() {
  const { id } = useParams<{ id: string }>()

  const [indexCourant, setIndexCourant] = useState(0)
  const [dialogOuvert, setDialogOuvert] = useState(false)

  const {
    copie,
    questions,
    chargement,
    erreur,
    reponses,
    setReponses,
    tempsRestant,
    tempsRestantFormate,
    derniereSauvegarde,
    erreurSauvegarde,
    soumettre,
    soumissionEnCours,
    erreurSoumission,
    avertissement,
  } = usePassation({ evaluationId: id, depuisCopie, versReponses, compterTraitees })

  const { changementsOnglet } = useAntiCheat()

  // ---------- Chargement / cas d'erreur ----------
  if (chargement) return <EtatPassation message="Chargement de votre copie..." retour={false} />
  if (erreur || !copie) return <EtatPassation message={erreur ?? "Évaluation introuvable."} />
  if (questions.length === 0) {
    return <EtatPassation message="Cette évaluation ne contient aucune question." />
  }

  const question = questions[indexCourant]
  const reponseCourante = reponses[question.id] ?? ""
  const nbRepondues = compterTraitees(reponses)
  const sansReponse = messageSansReponse(
    numerosSansReponse(questions, (id) => (reponses[id] ?? "").trim().length > 0)
  )
  const progression = (nbRepondues / questions.length) * 100

  const nbMots = reponseCourante.trim()
    ? reponseCourante.trim().split(/\s+/).length
    : 0

  const tempsCritique = tempsRestant < 300

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      {/* Titre de l'évaluation */}
      <p className="text-sm font-semibold text-muted-foreground">
        {copie.titreEvaluation}
      </p>

      {/* Barre supérieure : timer + sauvegarde */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <span
              className={`flex items-center gap-2 rounded-xl px-3 py-1.5 font-mono text-lg font-bold ${
                tempsCritique
                  ? "bg-destructive/10 text-destructive"
                  : "bg-primary/10 text-primary"
              }`}
            >
              <Clock className="size-4" />
              {tempsRestantFormate}
            </span>
            <span className="text-sm text-muted-foreground">
              {nbRepondues} / {questions.length} traitées
            </span>
          </div>

          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Save className="size-3.5" />
            {erreurSauvegarde
              ? "Hors ligne : réponses conservées sur cet appareil"
              : derniereSauvegarde
                ? `Sauvegardé à ${derniereSauvegarde}`
                : "Sauvegarde auto activée"}
          </span>
        </CardContent>
      </Card>

      {/* Copie rechargée après un conflit de version */}
      {avertissement && (
        <div className="flex items-center gap-2 rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-2.5 text-sm text-amber-700 dark:text-amber-400">
          <Info className="size-4 shrink-0" />
          <span>{avertissement}</span>
        </div>
      )}

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

      {/* Progression */}
      <Progress value={progression} className="h-2" />

      {/* Question courante */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <CardTitle className="text-base font-bold">
            Question {indexCourant + 1} sur {questions.length}
          </CardTitle>
          <Badge
            variant="outline"
            className="rounded-full border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary"
          >
            {question.points} pts
          </Badge>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <p className="text-base font-medium leading-relaxed">
            {question.enonce}
          </p>

          <Textarea
            value={reponseCourante}
            maxLength={TAILLE_MAX_TEXTE}
            onChange={(e) => {
              // Valeur lue tout de suite : la fonction de mise à jour s'exécute plus tard
              const texte = e.target.value.slice(0, TAILLE_MAX_TEXTE)
              setReponses((r) => ({ ...r, [question.id]: texte }))
            }}
            placeholder="Rédigez votre réponse ici..."
            className="min-h-56 resize-y rounded-xl text-sm leading-relaxed"
          />

          <p className="text-right text-xs text-muted-foreground">
            {nbMots} mot{nbMots > 1 ? "s" : ""} · {reponseCourante.length}{" "}
            caractère{reponseCourante.length > 1 ? "s" : ""}
          </p>
          {reponseCourante.length >= TAILLE_MAX_TEXTE && (
            <p className="text-right text-xs font-medium text-destructive">
              Limite de {formatNombre(TAILLE_MAX_TEXTE)} caractères atteinte.
            </p>
          )}
        </CardContent>
      </Card>

      {/* Navigation entre questions */}
      <div className="flex items-center justify-between gap-3">
        <Button
          variant="outline"
          disabled={indexCourant === 0}
          onClick={() => setIndexCourant((i) => i - 1)}
        >
          <ChevronLeft className="mr-1 size-4" />
          Précédent
        </Button>

        <div className="flex flex-wrap justify-center gap-2">
          {questions.map((q, i) => {
            const traitee = (reponses[q.id] ?? "").trim().length > 0
            return (
              <button
                key={q.id}
                onClick={() => setIndexCourant(i)}
                className={`size-9 rounded-lg text-sm font-semibold transition-colors ${
                  i === indexCourant
                    ? "bg-primary text-primary-foreground"
                    : traitee
                      ? "bg-primary/15 text-primary"
                      : "bg-muted text-muted-foreground hover:bg-muted/70"
                }`}
              >
                {i + 1}
              </button>
            )
          })}
        </div>

        {indexCourant < questions.length - 1 ? (
          <Button onClick={() => setIndexCourant((i) => i + 1)}>
            Suivant
            <ChevronRight className="ml-1 size-4" />
          </Button>
        ) : (
          <Button className="shadow-md" onClick={() => setDialogOuvert(true)}>
            <Send className="mr-1 size-4" />
            Soumettre
          </Button>
        )}
      </div>

      {indexCourant < questions.length - 1 && (
        <Button
          variant="outline"
          className="self-center"
          onClick={() => setDialogOuvert(true)}
        >
          <Send className="mr-1 size-4" />
          Terminer et soumettre
        </Button>
      )}

      {/* Modale de confirmation */}
      <Dialog open={dialogOuvert} onOpenChange={setDialogOuvert}>
        <DialogContent
          showCloseButton={false}
          className="max-w-md rounded-2xl p-6 text-sm"
        >
          <DialogHeader className="gap-2">
            <DialogTitle className="text-lg font-bold">
              Confirmer la soumission
            </DialogTitle>
            <DialogDescription className="text-sm leading-relaxed">
              Vous avez traité {nbRepondues} question
              {nbRepondues > 1 ? "s" : ""} sur {questions.length}.
              {sansReponse && ` ${sansReponse}`}{" "}
              Cette action est définitive.
            </DialogDescription>
          </DialogHeader>

          {erreurSoumission && (
            <p className="mt-3 text-sm text-destructive">{erreurSoumission}</p>
          )}

          <div className="mt-4 flex w-full min-w-0 flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => setDialogOuvert(false)}
            >
              Continuer
            </Button>
            <Button
              className="w-full shadow-md sm:w-auto"
              disabled={soumissionEnCours}
              onClick={() => soumettre()}
            >
              <CircleCheck className="mr-1 size-4 shrink-0" />
              {soumissionEnCours ? "Envoi..." : "Soumettre"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}