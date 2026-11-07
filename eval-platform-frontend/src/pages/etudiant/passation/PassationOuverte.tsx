import { useState } from "react"
import { useParams } from "react-router-dom"
import { Textarea } from "@/components/ui/textarea"
import { usePassation } from "@/hooks/usePassation"
import { formatNombre, TAILLE_MAX_TEXTE } from "@/lib/passation"
import EtatPassation from "./EtatPassation"
import PassationShell from "./PassationShell"
import type { QuestionExamen } from "@/types"

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

  const passation = usePassation({ evaluationId: id, depuisCopie, versReponses, compterTraitees })
  const { copie, questions, chargement, erreur, reponses, setReponses } = passation

  // ---------- Chargement / cas d'erreur ----------
  if (chargement) return <EtatPassation message="Chargement de votre copie..." retour={false} />
  if (erreur || !copie) return <EtatPassation message={erreur ?? "Évaluation introuvable."} />
  if (questions.length === 0) {
    return <EtatPassation message="Cette évaluation ne contient aucune question." />
  }

  const question = questions[indexCourant]
  const reponseCourante = reponses[question.id] ?? ""
  const nbMots = reponseCourante.trim() ? reponseCourante.trim().split(/\s+/).length : 0

  return (
    <PassationShell
      {...passation}
      titreEvaluation={copie.titreEvaluation}
      indexCourant={indexCourant}
      onChangerQuestion={setIndexCourant}
      estTraitee={(questionId) => (reponses[questionId] ?? "").trim().length > 0}
      vocabulaire="traite"
    >
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
    </PassationShell>
  )
}
