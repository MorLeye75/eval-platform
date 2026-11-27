/**
 * Barème négatif : l'étudiant doit savoir qu'une mauvaise réponse coûte des points, pour choisir de répondre ou de
 * laisser la question vide. Rien n'est affiché sans pénalité.
 */
export default function MentionPenalite({ penalite }: { penalite?: number | null }) {
  if (!penalite || penalite <= 0) return null
  const points = penalite.toLocaleString("fr-FR")
  return (
    <p className="text-xs font-medium text-amber-700 dark:text-amber-400">
      Mauvaise réponse : −{points} pt · sans réponse : 0
    </p>
  )
}
