import { useState, type ReactNode } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { messageSansReponse, numerosSansReponse } from "@/lib/passation"
import type { QuestionExamen } from "@/types"
import { ChevronLeft, ChevronRight, CircleCheck, Clock, Eye, Info, Save, Send } from "lucide-react"

/** Sous ce seuil (5 min), le chrono passe en rouge. */
const SECONDES_TEMPS_CRITIQUE = 300

interface PassationShellProps {
  titreEvaluation: string
  questions: QuestionExamen[]
  indexCourant: number
  onChangerQuestion: (index: number) => void
  /** Une question compte comme traitée (compteur, grille, questions sans réponse). */
  estTraitee: (questionId: number) => boolean
  /** « répondu » pour les QCM, « traité » pour les réponses rédigées ou le code. */
  vocabulaire: "repondu" | "traite"
  tempsRestant: number
  tempsRestantFormate: string
  derniereSauvegarde: string | null
  erreurSauvegarde: boolean
  avertissement: string | null
  changementsOnglet: number
  soumettre: () => void
  soumissionEnCours: boolean
  erreurSoumission: string | null
  /** Phrase ajoutée à la confirmation de soumission (ex. tests cachés pour le code). */
  noteConfirmation?: string
  largeur?: "max-w-3xl" | "max-w-4xl"
  /** Corps de la question courante (énoncé, saisie de la réponse). */
  children: ReactNode
}

/**
 * Cadre commun des écrans de passation (QCM, questions ouvertes, code) : en-tête avec chrono et sauvegarde,
 * bandeaux, progression, carte de la question, navigation et confirmation de soumission (DS-01).
 */
export default function PassationShell({
  titreEvaluation,
  questions,
  indexCourant,
  onChangerQuestion,
  estTraitee,
  vocabulaire,
  tempsRestant,
  tempsRestantFormate,
  derniereSauvegarde,
  erreurSauvegarde,
  avertissement,
  changementsOnglet,
  soumettre,
  soumissionEnCours,
  erreurSoumission,
  noteConfirmation,
  largeur = "max-w-3xl",
  children,
}: PassationShellProps) {
  const [dialogOuvert, setDialogOuvert] = useState(false)

  const question = questions[indexCourant]
  const nbTraitees = questions.filter((q) => estTraitee(q.id)).length
  const sansReponse = messageSansReponse(numerosSansReponse(questions, estTraitee))
  const progression = (nbTraitees / questions.length) * 100
  const tempsCritique = tempsRestant < SECONDES_TEMPS_CRITIQUE
  const derniere = indexCourant === questions.length - 1

  return (
    <div className={`mx-auto flex ${largeur} flex-col gap-5`}>
      {/* Titre de l'évaluation */}
      <p className="text-sm font-semibold text-muted-foreground">{titreEvaluation}</p>

      {/* Barre supérieure : timer + sauvegarde */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <span
              className={`flex items-center gap-2 rounded-xl px-3 py-1.5 font-mono text-lg font-bold ${
                tempsCritique ? "bg-destructive/10 text-destructive" : "bg-primary/10 text-primary"
              }`}
            >
              <Clock className="size-4" />
              {tempsRestantFormate}
            </span>
            <span className="text-sm text-muted-foreground">
              {nbTraitees} / {questions.length} {vocabulaire === "repondu" ? "répondues" : "traitées"}
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
          <p className="text-base font-medium leading-relaxed">{question.enonce}</p>
          {children}
        </CardContent>
      </Card>

      {/* Navigation entre questions */}
      <div className="flex items-center justify-between gap-3">
        <Button variant="outline" disabled={indexCourant === 0} onClick={() => onChangerQuestion(indexCourant - 1)}>
          <ChevronLeft className="mr-1 size-4" />
          Précédent
        </Button>

        <div className="flex flex-wrap justify-center gap-2">
          {questions.map((q, i) => (
            <button
              key={q.id}
              type="button"
              aria-current={i === indexCourant ? "step" : undefined}
              onClick={() => onChangerQuestion(i)}
              className={`size-9 rounded-lg text-sm font-semibold transition-colors ${
                i === indexCourant
                  ? "bg-primary text-primary-foreground"
                  : estTraitee(q.id)
                    ? "bg-primary/15 text-primary"
                    : "bg-muted text-muted-foreground hover:bg-muted/70"
              }`}
            >
              {i + 1}
            </button>
          ))}
        </div>

        {!derniere ? (
          <Button onClick={() => onChangerQuestion(indexCourant + 1)}>
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

      {/* Bouton soumettre toujours accessible */}
      {!derniere && (
        <Button variant="outline" className="self-center" onClick={() => setDialogOuvert(true)}>
          <Send className="mr-1 size-4" />
          Terminer et soumettre
        </Button>
      )}

      {/* Confirmation de soumission. Pendant l'envoi, la fenêtre reste ouverte : l'étudiant voit l'issue de sa remise */}
      <Dialog
        open={dialogOuvert}
        onOpenChange={(ouvert) => {
          if (!soumissionEnCours) setDialogOuvert(ouvert)
        }}
      >
        <DialogContent showCloseButton={false} className="max-w-md rounded-2xl p-6 text-sm">
          <DialogHeader className="gap-2">
            <DialogTitle className="text-lg font-bold">Confirmer la soumission</DialogTitle>
            <DialogDescription className="text-sm leading-relaxed">
              {vocabulaire === "repondu" ? "Vous avez répondu à" : "Vous avez traité"} {nbTraitees} question
              {nbTraitees > 1 ? "s" : ""} sur {questions.length}.
              {sansReponse && ` ${sansReponse}`}
              {noteConfirmation && ` ${noteConfirmation}`} Cette action est définitive.
            </DialogDescription>
          </DialogHeader>

          {erreurSoumission && <p className="mt-3 text-sm text-destructive">{erreurSoumission}</p>}

          <div className="mt-4 flex w-full min-w-0 flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              variant="outline"
              className="w-full sm:w-auto"
              disabled={soumissionEnCours}
              onClick={() => setDialogOuvert(false)}
            >
              Continuer
            </Button>
            <Button className="w-full shadow-md sm:w-auto" disabled={soumissionEnCours} onClick={() => soumettre()}>
              <CircleCheck className="mr-1 size-4 shrink-0" />
              {soumissionEnCours ? "Envoi..." : "Soumettre"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
