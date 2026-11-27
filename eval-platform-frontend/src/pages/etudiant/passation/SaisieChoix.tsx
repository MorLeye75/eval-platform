import { Checkbox } from "@/components/ui/checkbox"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import type { QuestionExamen } from "@/types"

interface SaisieChoixProps {
  question: QuestionExamen
  choixIds: number[]
  onChange: (choixIds: number[]) => void
}

/** Réponse à une question à choix (QCM) : un seul choix, ou plusieurs si la question le permet. */
export default function SaisieChoix({ question, choixIds, onChange }: SaisieChoixProps) {
  if (question.estChoixMultiple) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-xs text-muted-foreground">Plusieurs réponses possibles</p>
        {question.choixPossibles.map((choix) => (
          <label
            key={choix.id}
            className="flex cursor-pointer items-center gap-3 rounded-xl border border-border/60 p-3.5 transition-colors hover:bg-muted/50"
          >
            <Checkbox
              checked={choixIds.includes(choix.id)}
              onCheckedChange={(coche) =>
                onChange(coche === true ? [...choixIds, choix.id] : choixIds.filter((c) => c !== choix.id))
              }
            />
            <span className="text-sm">{choix.texte}</span>
          </label>
        ))}
      </div>
    )
  }

  return (
    <RadioGroup
      value={String(choixIds[0] ?? "")}
      onValueChange={(v) => onChange([Number(v)])}
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
  )
}
