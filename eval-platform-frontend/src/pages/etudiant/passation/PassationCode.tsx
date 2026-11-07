import { useState } from "react"
import { useParams } from "react-router-dom"
import Editor from "@monaco-editor/react"
import { Button } from "@/components/ui/button"
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
import { Play } from "lucide-react"

// Langages proposés dans l'éditeur (id de langage reconnu par Monaco -> libellé)
const LANGAGES_LABELS: Record<string, string> = {
  c: "C",
  cpp: "C++",
  java: "Java",
  python: "Python",
  javascript: "JavaScript",
}

const LANGAGES = Object.keys(LANGAGES_LABELS)
const LANGAGE_PAR_DEFAUT = LANGAGES[0]

/** Réponse de code : le langage en fait partie (enregistré avec le code, nécessaire pour l'exécuter à la correction). */
interface ReponseCodeLocale {
  code: string
  langage: string
}

type ReponsesCode = Record<number, ReponseCodeLocale> // questionId -> code et langage

function depuisCopie(questions: QuestionExamen[]): ReponsesCode {
  return Object.fromEntries(
    questions.map((q) => [
      q.id,
      { code: q.reponseActuelle?.codeSoumis ?? "", langage: q.reponseActuelle?.langage ?? LANGAGE_PAR_DEFAUT },
    ])
  )
}

function versReponses(codes: ReponsesCode) {
  return Object.entries(codes).map(([questionId, r]) => ({
    questionId: Number(questionId),
    codeSoumis: r.code,
    langage: r.langage,
  }))
}

function compterTraitees(codes: ReponsesCode) {
  return Object.values(codes).filter((r) => r.code.trim().length > 0).length
}

export default function PassationCode() {
  const { id } = useParams<{ id: string }>()
  const [indexCourant, setIndexCourant] = useState(0)

  const passation = usePassation({ evaluationId: id, depuisCopie, versReponses, compterTraitees })
  const { copie, questions, chargement, erreur, reponses: codes, setReponses: setCodes } = passation

  // ---------- Chargement / cas d'erreur ----------
  if (chargement) return <EtatPassation message="Chargement de votre copie..." retour={false} />
  if (erreur || !copie) return <EtatPassation message={erreur ?? "Évaluation introuvable."} />
  if (questions.length === 0) {
    return <EtatPassation message="Cette évaluation ne contient aucune question." />
  }

  const question = questions[indexCourant]
  const reponseCourante = codes[question.id] ?? { code: "", langage: LANGAGE_PAR_DEFAUT }
  const casVisibles = question.casDeTestsVisibles

  function modifier(patch: Partial<ReponseCodeLocale>) {
    setCodes((c) => ({
      ...c,
      [question.id]: { ...(c[question.id] ?? { code: "", langage: LANGAGE_PAR_DEFAUT }), ...patch },
    }))
  }

  function changerLangage(langage: string | null) {
    if (langage) modifier({ langage })
  }

  function changerCode(valeur: string | undefined) {
    // Au-delà de la limite du serveur, chaque sauvegarde serait refusée : le code est coupé et la limite signalée
    modifier({ code: (valeur ?? "").slice(0, TAILLE_MAX_CODE) })
  }

  return (
    <PassationShell
      {...passation}
      titreEvaluation={copie.titreEvaluation}
      indexCourant={indexCourant}
      onChangerQuestion={setIndexCourant}
      estTraitee={(questionId) => (codes[questionId]?.code ?? "").trim().length > 0}
      vocabulaire="traite"
      noteConfirmation="Le code sera exécuté contre les cas de test, visibles et cachés, lors de la correction."
      largeur="max-w-4xl"
    >
      {/* Sélecteur de langage */}
      <div className="flex items-center gap-3">
        <span className="text-xs font-semibold text-muted-foreground">
          Langage
        </span>
        <Select value={reponseCourante.langage} onValueChange={changerLangage}>
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
          language={reponseCourante.langage}
          value={reponseCourante.code}
          onChange={changerCode}
          theme="vs-dark"
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            scrollBeyondLastLine: false,
          }}
        />
      </div>
      {reponseCourante.code.length >= TAILLE_MAX_CODE && (
        <p className="text-right text-xs font-medium text-destructive">
          Limite de {formatNombre(TAILLE_MAX_CODE)} caractères atteinte : la suite n'est pas enregistrée.
        </p>
      )}

      {/* Pas d'exécution pendant l'épreuve tant que l'exécuteur isolé n'est pas branché : un faux « Réussi » tromperait */}
      <div className="flex flex-col gap-1">
        <Button variant="outline" className="self-start" disabled>
          <Play className="mr-1 size-4" />
          Exécution disponible à la correction
        </Button>
        <p className="text-xs text-muted-foreground">
          Votre code n'est pas exécuté pendant l'épreuve : il le sera lors de la correction, contre les cas de test
          ci-dessous et des cas cachés.
        </p>
      </div>

      {/* Cas de tests visibles */}
      {casVisibles.length > 0 && (
        <div className="flex flex-col gap-2">
          <span className="text-xs font-semibold text-muted-foreground">
            Cas de test visibles
          </span>
          {casVisibles.map((cas) => (
            <div
              key={cas.id}
              className="flex flex-wrap items-center gap-3 rounded-xl border border-border/60 p-3 font-mono text-xs text-muted-foreground"
            >
              <span>
                Entrée : <span className="text-foreground">{cas.entree}</span>
              </span>
              <span>
                Attendu : <span className="text-foreground">{cas.sortieAttendue}</span>
              </span>
            </div>
          ))}
        </div>
      )}
    </PassationShell>
  )
}
