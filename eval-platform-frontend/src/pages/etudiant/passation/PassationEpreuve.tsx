import { useState } from "react"
import { useParams } from "react-router-dom"
import { usePassation } from "@/hooks/usePassation"
import EtatPassation from "./EtatPassation"
import PassationShell from "./PassationShell"
import SaisieChoix from "./SaisieChoix"
import SaisieTexte from "./SaisieTexte"
import SaisieCode from "./SaisieCode"
import {
  compterTraitees,
  depuisCopie,
  estRepondue,
  reponseVide,
  versReponses,
  type ReponseLocale,
} from "./reponsesEpreuve"

/**
 * Écran de passation de toute épreuve hors quiz chronométré : QCM, questions ouvertes, code, ou un mélange.
 * Chaque question s'affiche avec la saisie de son type (aucune question n'est perdue dans une épreuve mixte).
 */
export default function PassationEpreuve() {
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
  const reponse = reponses[question.id] ?? reponseVide(question)
  const aDuCode = questions.some((q) => q.type === "CODE")
  const queDesChoix = questions.every((q) => q.type === "QCM" || q.type === "QUIZ_CHRONOMETREE")

  function modifier(nouvelle: ReponseLocale) {
    setReponses((r) => ({ ...r, [question.id]: nouvelle }))
  }

  return (
    <PassationShell
      {...passation}
      titreEvaluation={copie.titreEvaluation}
      indexCourant={indexCourant}
      onChangerQuestion={setIndexCourant}
      estTraitee={(questionId) => estRepondue(reponses[questionId])}
      vocabulaire={queDesChoix ? "repondu" : "traite"}
      noteConfirmation={
        aDuCode ? "Le code sera exécuté contre les cas de test, visibles et cachés, lors de la correction." : undefined
      }
      largeur={aDuCode ? "max-w-4xl" : "max-w-3xl"}
    >
      {reponse.type === "choix" && (
        <SaisieChoix
          question={question}
          choixIds={reponse.choixIds}
          onChange={(choixIds) => modifier({ type: "choix", choixIds })}
        />
      )}
      {reponse.type === "texte" && (
        <SaisieTexte texte={reponse.texte} onChange={(texte) => modifier({ type: "texte", texte })} />
      )}
      {reponse.type === "code" && (
        <SaisieCode
          question={question}
          code={reponse.code}
          langage={reponse.langage}
          onChange={(patch) => modifier({ ...reponse, ...patch })}
        />
      )}
    </PassationShell>
  )
}
