import { useState, useRef } from "react"
import { useParams } from "react-router-dom"
import Editor from "@monaco-editor/react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { usePassation } from "@/hooks/usePassation"
import { formatNombre, TAILLE_MAX_CODE } from "@/lib/passation"
import EtatPassation from "./EtatPassation"
import PassationShell from "./PassationShell"
import type { QuestionExamen } from "@/types"
import { CircleCheck, CircleX, Play } from "lucide-react"

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
  // Version du code par question : incrémentée à chaque modification, elle permet
  // d'ignorer le résultat d'une exécution lancée sur un code devenu obsolète.
  const versionCode = useRef<Record<number, number>>({})

  const passation = usePassation({ evaluationId: id, depuisCopie, versReponses, compterTraitees })
  const { copie, questions, chargement, erreur, reponses: codes, setReponses: setCodes } = passation

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
    // Au-delà de la limite du serveur, chaque sauvegarde serait refusée : le code est coupé et la limite signalée
    setCodes((c) => ({ ...c, [question.id]: (valeur ?? "").slice(0, TAILLE_MAX_CODE) }))
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

  return (
    <PassationShell
      {...passation}
      titreEvaluation={copie.titreEvaluation}
      indexCourant={indexCourant}
      onChangerQuestion={setIndexCourant}
      estTraitee={(questionId) => (codes[questionId] ?? "").trim().length > 0}
      vocabulaire="traite"
      noteConfirmation="Le code sera aussi exécuté contre des cas de tests cachés lors de la correction."
      largeur="max-w-4xl"
    >
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
      {codeCourant.length >= TAILLE_MAX_CODE && (
        <p className="text-right text-xs font-medium text-destructive">
          Limite de {formatNombre(TAILLE_MAX_CODE)} caractères atteinte : la suite n'est pas enregistrée.
        </p>
      )}

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
    </PassationShell>
  )
}
