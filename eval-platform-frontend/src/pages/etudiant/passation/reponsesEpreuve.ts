import { LANGAGE_PAR_DEFAUT } from "@/lib/langages"
import type { QuestionExamen, ReponseItem } from "@/types"

/** Réponse locale à une question, selon son type (une épreuve peut mêler plusieurs types). */
export type ReponseLocale =
  | { type: "choix"; choixIds: number[] }
  | { type: "texte"; texte: string }
  | { type: "code"; code: string; langage: string }

export type ReponsesEpreuve = Record<number, ReponseLocale> // questionId -> réponse

/** Réponse vide adaptée au type de la question. */
export function reponseVide(question: QuestionExamen): ReponseLocale {
  if (question.type === "OUVERTE") return { type: "texte", texte: "" }
  if (question.type === "CODE") return { type: "code", code: "", langage: LANGAGE_PAR_DEFAUT }
  return { type: "choix", choixIds: [] } // QCM et quiz
}

/** Réponses du formulaire reconstruites depuis la copie enregistrée sur le serveur. */
export function depuisCopie(questions: QuestionExamen[]): ReponsesEpreuve {
  return Object.fromEntries(
    questions.map((q): [number, ReponseLocale] => {
      const r = q.reponseActuelle
      const vide = reponseVide(q)
      if (vide.type === "texte") return [q.id, { type: "texte", texte: r?.texteSoumis ?? "" }]
      if (vide.type === "code") {
        return [q.id, { type: "code", code: r?.codeSoumis ?? "", langage: r?.langage ?? LANGAGE_PAR_DEFAUT }]
      }
      return [q.id, { type: "choix", choixIds: r?.choixIds ?? [] }]
    })
  )
}

/** Réponses du formulaire converties pour PUT /soumissions/{id}/sauvegarder. */
export function versReponses(reponses: ReponsesEpreuve): ReponseItem[] {
  return Object.entries(reponses).map(([id, r]) => {
    const questionId = Number(id)
    if (r.type === "texte") return { questionId, texteSoumis: r.texte }
    if (r.type === "code") return { questionId, codeSoumis: r.code, langage: r.langage }
    return { questionId, choixIds: r.choixIds }
  })
}

/** Une réponse compte comme traitée : au moins un choix coché, ou un texte / un code non vide. */
export function estRepondue(r: ReponseLocale | undefined) {
  if (!r) return false
  if (r.type === "texte") return r.texte.trim().length > 0
  if (r.type === "code") return r.code.trim().length > 0
  return r.choixIds.length > 0
}

export function compterTraitees(reponses: ReponsesEpreuve) {
  return Object.values(reponses).filter(estRepondue).length
}
