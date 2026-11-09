import type {
  EvaluationEnseignantApi,
  EvaluationRequete,
  QuestionApi,
  QuestionRequete,
} from "@/lib/evaluationsEnseignant"
import type { BrouillonEvaluation, QuestionLocal } from "./brouillon"

// Passage entre le brouillon de l'assistant (champs de formulaire) et le contrat de l'API (DS-03).

/** ISO 8601 du serveur → valeur d'un <input type="datetime-local"> (AAAA-MM-JJTHH:mm). */
export function versDateTimeLocal(iso: string) {
  return iso.slice(0, 16)
}

function questionVersRequete(q: QuestionLocal): QuestionRequete {
  const base = { type: q.type, enonce: q.enonce.trim(), points: q.points }
  if (q.type === "QCM" || q.type === "QUIZ_CHRONOMETREE") {
    return {
      ...base,
      estChoixMultiple: q.estChoixMultiple ?? false,
      ...(q.type === "QUIZ_CHRONOMETREE" ? { dureeQuestion: q.dureeQuestion ?? 30 } : {}),
      choix: (q.choix ?? []).map((c) => ({ texte: c.texte.trim(), estCorrect: c.estCorrect })),
    }
  }
  if (q.type === "CODE") {
    return {
      ...base,
      // Entrée facultative : un champ vide est envoyé comme absent (le programme ne lit rien)
      casTests: (q.casTests ?? []).map((c) => ({
        entree: c.entree === "" ? null : c.entree,
        sortieAttendue: c.sortieAttendue,
        estMasque: c.estMasque,
      })),
    }
  }
  return base
}

/** Corps du POST de création ou du PUT de modification. L'ordre des questions est leur position. */
export function versRequete(brouillon: BrouillonEvaluation): EvaluationRequete {
  const { infos, questions } = brouillon
  return {
    titre: infos.titre.trim(),
    description: infos.description,
    dureeStandard: infos.duree,
    dateOuverture: infos.dateOuverture,
    dateFermeture: infos.dateFermeture,
    promotionIds: infos.promotionIds,
    questions: questions.map(questionVersRequete),
  }
}

function questionVersLocale(q: QuestionApi, index: number): QuestionLocal {
  // Identifiants locaux uniques : ils ne servent qu'aux clés React et aux sélections de l'assistant
  const localId = index + 1
  const base = { localId, type: q.type, enonce: q.enonce, points: q.points }
  if (q.type === "QCM" || q.type === "QUIZ_CHRONOMETREE") {
    return {
      ...base,
      estChoixMultiple: q.estChoixMultiple ?? false,
      ...(q.type === "QUIZ_CHRONOMETREE" ? { dureeQuestion: q.dureeQuestion ?? 30 } : {}),
      choix: q.choix.map((c, i) => ({ id: (index + 1) * 1000 + i + 1, texte: c.texte, estCorrect: c.estCorrect })),
    }
  }
  if (q.type === "CODE") {
    return {
      ...base,
      casTests: q.casTests.map((c, i) => ({
        id: (index + 1) * 1000 + i + 1,
        entree: c.entree ?? "",
        sortieAttendue: c.sortieAttendue,
        estMasque: c.estMasque,
      })),
    }
  }
  return base
}

/**
 * Brouillon d'édition construit depuis le détail renvoyé par le serveur. Seul un brouillon est modifiable : le
 * statut initial reste « brouillon » tant que l'enseignant ne choisit pas de publier.
 */
export function versBrouillon(evaluation: EvaluationEnseignantApi): BrouillonEvaluation {
  return {
    evaluationId: evaluation.id,
    infos: {
      titre: evaluation.titre,
      description: evaluation.description ?? "",
      duree: evaluation.dureeStandard,
      dateOuverture: versDateTimeLocal(evaluation.dateOuverture),
      dateFermeture: versDateTimeLocal(evaluation.dateFermeture),
      statut: "BROUILLON",
      promotionIds: evaluation.promotionIds,
    },
    questions: [...evaluation.questions].sort((a, b) => a.ordre - b.ordre).map(questionVersLocale),
  }
}
