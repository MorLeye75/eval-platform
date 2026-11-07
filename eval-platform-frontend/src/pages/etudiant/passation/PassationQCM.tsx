import { useState } from "react"
import { useParams } from "react-router-dom"
import { Checkbox } from "@/components/ui/checkbox"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { usePassation } from "@/hooks/usePassation"
import EtatPassation from "./EtatPassation"
import PassationShell from "./PassationShell"
import type { QuestionExamen } from "@/types"

type ReponsesQCM = Record<number, number[]> // questionId -> choixIds cochés

function depuisCopie(questions: QuestionExamen[]): ReponsesQCM {
  return Object.fromEntries(
    questions.map((q) => [q.id, q.reponseActuelle?.choixIds ?? []])
  )
}

function versReponses(reponses: ReponsesQCM) {
  return Object.entries(reponses).map(([questionId, choixIds]) => ({
    questionId: Number(questionId),
    choixIds,
  }))
}

function compterTraitees(reponses: ReponsesQCM) {
  return Object.values(reponses).filter((r) => r.length > 0).length
}

export default function PassationQCM() {
  const { id } = useParams<{ id: string }>()
  const [indexCourant, setIndexCourant] = useState(0)

  const passation = usePassation({ evaluationId: id, depuisCopie, versReponses, compterTraitees })
  const { copie, questions, chargement, erreur, reponses, setReponses } = passation

  // ---------- Chargement / cas d'erreur ----------
  if (chargement) return <EtatPassation message="Chargement de votre copie..." retour={false} />
  if (erreur || !copie) return <EtatPassation message={erreur ?? "Évaluation introuvable."} />
  if (questions.length === 0) {
    return <EtatPassation message="Cette évaluation ne contient aucune question." />
  }

  const question = questions[indexCourant]
  const cochees = reponses[question.id] ?? []

  // ---------- Gestion des réponses ----------
  function selectionUnique(choixId: number) {
    setReponses((r) => ({ ...r, [question.id]: [choixId] }))
  }

  function selectionMultiple(choixId: number, coche: boolean) {
    setReponses((r) => {
      const actuelles = r[question.id] ?? []
      return {
        ...r,
        [question.id]: coche ? [...actuelles, choixId] : actuelles.filter((c) => c !== choixId),
      }
    })
  }

  return (
    <PassationShell
      {...passation}
      titreEvaluation={copie.titreEvaluation}
      indexCourant={indexCourant}
      onChangerQuestion={setIndexCourant}
      estTraitee={(questionId) => (reponses[questionId] ?? []).length > 0}
      vocabulaire="repondu"
    >
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
              <RadioGroupItem value={String(choix.id)} id={`c-${choix.id}`} />
              <span className="text-sm">{choix.texte}</span>
            </label>
          ))}
        </RadioGroup>
      )}
    </PassationShell>
  )
}
