import { useState, useEffect, useRef } from "react"
import { useParams, useNavigate, Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Checkbox } from "@/components/ui/checkbox"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { getEvaluation } from "@/mocks/evaluations"
import {
  Clock,
  ChevronLeft,
  ChevronRight,
  Save,
  Send,
  CircleCheck,
  Eye,
} from "lucide-react"

export default function PassationQCM() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const evaluation = getEvaluation(id)
  const questions = evaluation?.questions ?? []

  const [indexCourant, setIndexCourant] = useState(0)
  const [reponses, setReponses] = useState<Record<number, number[]>>({})
  const [tempsRestant, setTempsRestant] = useState(
    (evaluation?.duree ?? 45) * 60
  )
  const [derniereSauvegarde, setDerniereSauvegarde] = useState<string | null>(null)
  const [dialogOuvert, setDialogOuvert] = useState(false)
  const [changementsOnglet, setChangementsOnglet] = useState(0)

  // ---------- Timer (côté client pour l'instant ; à recalculer serveur plus tard) ----------
  useEffect(() => {
    if (!evaluation) return
    const timer = setInterval(() => {
      setTempsRestant((t) => {
        if (t <= 1) {
          clearInterval(timer)
          navigate(`/examen/${id}/expiration`)
          return 0
        }
        return t - 1
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [id, navigate, evaluation])

  // ---------- Sauvegarde automatique toutes les 30 s ----------
  // TODO backend : remplacer par PUT /api/soumissions/{id}/sauvegarder
  const reponsesRef = useRef(reponses)
  reponsesRef.current = reponses

  useEffect(() => {
    const autoSave = setInterval(() => {
      const maintenant = new Date().toLocaleTimeString("fr-FR", {
        hour: "2-digit",
        minute: "2-digit",
      })
      setDerniereSauvegarde(maintenant)
      console.log("Sauvegarde auto (mock) :", reponsesRef.current)
    }, 30000)
    return () => clearInterval(autoSave)
  }, [])

  // ---------- Anti-triche : détection de changement d'onglet ----------
  // TODO backend : envoyer un log au serveur à chaque détection
  useEffect(() => {
    function handleVisibilite() {
      if (document.hidden) {
        setChangementsOnglet((n) => n + 1)
      }
    }
    document.addEventListener("visibilitychange", handleVisibilite)
    return () => document.removeEventListener("visibilitychange", handleVisibilite)
  }, [])

  // ---------- Cas d'erreur : évaluation introuvable ou sans questions ----------
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
          render={
            <Link to="/etudiant/evaluations">Retour aux évaluations</Link>
          }
        />
      </div>
    )
  }

  const question = questions[indexCourant]
  const nbRepondues = Object.values(reponses).filter((r) => r.length > 0).length
  const progression = (nbRepondues / questions.length) * 100

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

  function formatTemps(secondes: number) {
    const m = Math.floor(secondes / 60)
    const s = secondes % 60
    return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
  }

  const tempsCritique = tempsRestant < 300 // moins de 5 min

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      {/* Titre de l'évaluation */}
      <p className="text-sm font-semibold text-muted-foreground">
        {evaluation.titre}
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
              {formatTemps(tempsRestant)}
            </span>
            <span className="text-sm text-muted-foreground">
              {nbRepondues} / {questions.length} répondues
            </span>
          </div>

          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Save className="size-3.5" />
            {derniereSauvegarde
              ? `Sauvegardé à ${derniereSauvegarde}`
              : "Sauvegarde auto activée"}
          </span>
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
        <CardContent className="flex flex-col gap-5">
          <p className="text-base font-medium">{question.enonce}</p>

          {question.estChoixMultiple ? (
            <div className="flex flex-col gap-3">
              <p className="text-xs text-muted-foreground">
                Plusieurs réponses possibles
              </p>
              {question.choix.map((choix) => (
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
              {question.choix.map((choix) => (
                <label
                  key={choix.id}
                  className="flex cursor-pointer items-center gap-3 rounded-xl border border-border/60 p-3.5 transition-colors hover:bg-muted/50"
                >
                  <RadioGroupItem value={String(choix.id)} id={`c-${choix.id}`} />
                  <span className="text-sm">{choix.texte}</span>
                </label>
              ))}
            </RadioGroup>
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

        {/* Grille des numéros de question */}
        <div className="flex flex-wrap justify-center gap-2">
          {questions.map((q, i) => {
            const repondue = (reponses[q.id] ?? []).length > 0
            return (
              <button
                key={q.id}
                onClick={() => setIndexCourant(i)}
                className={`size-9 rounded-lg text-sm font-semibold transition-colors ${
                  i === indexCourant
                    ? "bg-primary text-primary-foreground"
                    : repondue
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

      {/* Bouton soumettre toujours accessible */}
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

      {/* Modale de confirmation de soumission */}
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
              Vous avez répondu à {nbRepondues} question
              {nbRepondues > 1 ? "s" : ""} sur {questions.length}.
              {nbRepondues < questions.length &&
                " Les questions sans réponse seront comptées comme non traitées."}{" "}
              Cette action est définitive.
            </DialogDescription>
          </DialogHeader>

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
              onClick={() => {
                // TODO backend : POST /api/soumissions/{id}/soumettre
                navigate(`/examen/${id}/confirmation`)
              }}
            >
              <CircleCheck className="mr-1 size-4 shrink-0" />
              Soumettre
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}