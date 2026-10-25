import { useState, useRef } from "react"
import { useParams } from "react-router-dom"
import Editor from "@monaco-editor/react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { usePassation } from "@/hooks/usePassation"
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
  Play,
  CircleX,
} from "lucide-react"

// Langages proposés dans l'éditeur (id de langage reconnu par Monaco -> libellé)
const LANGAGES_LABELS: Record<string, string> = {
  c: "C",
  cpp: "C++",
  java: "Java",
  python: "Python",
  javascript: "JavaScript",
}

const LANGAGES = Object.keys(LANGAGES_LABELS)

type ReponsesCode = Record<number, string> // questionId -> code saisi

function depuisCopie(questions: QuestionExamen[]): ReponsesCode {
  return Object.fromEntries(
    questions.map((q) => [q.id, q.reponseActuelle?.codeSoumis ?? ""])
  )
}

function versReponses(codes: ReponsesCode) {
  return Object.entries(codes).map(([questionId, codeSoumis]) => ({
    questionId: Number(questionId),
    codeSoumis,
  }))
}

function compterTraitees(codes: ReponsesCode) {
  return Object.values(codes).filter((c) => c.trim().length > 0).length
}

interface ResultatExecution {
  casId: number
  reussi: boolean
  sortieObtenue: string
}

export default function PassationCode() {
  const { id } = useParams<{ id: string }>()

  const [indexCourant, setIndexCourant] = useState(0)
  const [langages, setLangages] = useState<Record<number, string>>({})
  const [resultats, setResultats] = useState<Record<number, ResultatExecution[]>>({})
  const [execution, setExecution] = useState(false)
  const [dialogOuvert, setDialogOuvert] = useState(false)
  // Version du code par question : incrémentée à chaque modification, elle permet
  // d'ignorer le résultat d'une exécution lancée sur un code devenu obsolète.
  const versionCode = useRef<Record<number, number>>({})

  const {
    copie,
    questions,
    chargement,
    erreur,
    reponses: codes,
    setReponses: setCodes,
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
  const langageCourant = langages[question.id] ?? LANGAGES[0]
  const codeCourant = codes[question.id] ?? ""
  const casVisibles = question.casDeTestsVisibles
  const resultatsCourants = resultats[question.id] ?? []

  const nbTraitees = compterTraitees(codes)
  const progression = (nbTraitees / questions.length) * 100

  function invaliderResultats(questionId: number) {
    versionCode.current[questionId] = (versionCode.current[questionId] ?? 0) + 1
    setResultats((r) => ({ ...r, [questionId]: [] }))
  }

  function changerLangage(langage: string | null) {
    if (!langage) return
    setLangages((l) => ({ ...l, [question.id]: langage }))
    invaliderResultats(question.id)
  }

  function changerCode(valeur: string | undefined) {
    setCodes((c) => ({ ...c, [question.id]: valeur ?? "" }))
    invaliderResultats(question.id)
  }

  async function executerCode() {
    const questionId = question.id
    const version = versionCode.current[questionId] ?? 0
    setExecution(true)
    // TODO backend : POST /api/judge0/executer { langage, code, casDeTests }
    await new Promise((r) => setTimeout(r, 900)) // simulation
    setExecution(false)
    // Code modifié pendant l'exécution : ces résultats ne correspondent plus au code affiché
    if ((versionCode.current[questionId] ?? 0) !== version) return
    const resultatsSimules: ResultatExecution[] = casVisibles.map((c) => ({
      casId: c.id,
      reussi: true,
      sortieObtenue: c.sortieAttendue,
    }))
    setResultats((r) => ({ ...r, [questionId]: resultatsSimules }))
  }

  const tempsCritique = tempsRestant < 300

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-5">
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
              {nbTraitees} / {questions.length} traitées
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

          {/* Sélecteur de langage */}
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold text-muted-foreground">
              Langage
            </span>
            <Select value={langageCourant} onValueChange={changerLangage}>
              <SelectTrigger className="w-44 rounded-xl">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {LANGAGES.map((l) => (
                  <SelectItem key={l} value={l}>
                    {LANGAGES_LABELS[l] ?? l}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Éditeur de code */}
          <div className="overflow-hidden rounded-xl border border-border/60">
            <Editor
              height="320px"
              language={langageCourant}
              value={codeCourant}
              onChange={changerCode}
              theme="vs-dark"
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                scrollBeyondLastLine: false,
              }}
            />
          </div>

          <Button
            variant="outline"
            className="self-start"
            disabled={execution || !codeCourant.trim()}
            onClick={executerCode}
          >
            <Play className="mr-1 size-4" />
            {execution ? "Exécution..." : "Exécuter contre les tests"}
          </Button>

          {/* Cas de tests visibles */}
          {casVisibles.length > 0 && (
            <div className="flex flex-col gap-2">
              <span className="text-xs font-semibold text-muted-foreground">
                Cas de test visibles
              </span>
              {casVisibles.map((cas) => {
                const resultat = resultatsCourants.find((r) => r.casId === cas.id)
                return (
                  <div
                    key={cas.id}
                    className="flex flex-col gap-1.5 rounded-xl border border-border/60 p-3 text-xs"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="flex flex-wrap items-center gap-3 font-mono text-muted-foreground">
                        <span>
                          Entrée : <span className="text-foreground">{cas.entree}</span>
                        </span>
                        <span>
                          Attendu :{" "}
                          <span className="text-foreground">{cas.sortieAttendue}</span>
                        </span>
                      </div>
                      {resultat && (
                        <Badge
                          variant="outline"
                          className={
                            resultat.reussi
                              ? "gap-1 rounded-full border-primary/20 bg-primary/10 text-primary"
                              : "gap-1 rounded-full border-destructive/20 bg-destructive/10 text-destructive"
                          }
                        >
                          {resultat.reussi ? (
                            <CircleCheck className="size-3" />
                          ) : (
                            <CircleX className="size-3" />
                          )}
                          {resultat.reussi ? "Réussi" : "Échoué"}
                        </Badge>
                      )}
                    </div>
                    {resultat && !resultat.reussi && (
                      <span className="font-mono text-destructive">
                        Sortie obtenue : {resultat.sortieObtenue}
                      </span>
                    )}
                  </div>
                )
              })}
            </div>
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
            const traitee = (codes[q.id] ?? "").trim().length > 0
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
              Vous avez traité {nbTraitees} question
              {nbTraitees > 1 ? "s" : ""} sur {questions.length}.
              {nbTraitees < questions.length &&
                " Les questions laissées vides seront comptées comme non traitées."}{" "}
              Le code sera aussi exécuté contre des cas de tests cachés lors de
              la correction. Cette action est définitive.
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
