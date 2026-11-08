import Editor from "@monaco-editor/react"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { LANGAGES, LANGAGES_LABELS } from "@/lib/langages"
import { formatNombre, TAILLE_MAX_CODE } from "@/lib/passation"
import type { QuestionExamen } from "@/types"
import { Play } from "lucide-react"

interface SaisieCodeProps {
  question: QuestionExamen
  code: string
  langage: string
  onChange: (patch: { code?: string; langage?: string }) => void
}

/**
 * Réponse de code : langage (enregistré avec la réponse) et éditeur. Pas d'exécution pendant l'épreuve tant que
 * l'exécuteur isolé n'est pas branché : un faux « Réussi » tromperait l'étudiant.
 */
export default function SaisieCode({ question, code, langage, onChange }: SaisieCodeProps) {
  const casVisibles = question.casDeTestsVisibles
  return (
    <>
      {/* Sélecteur de langage */}
      <div className="flex items-center gap-3">
        <span className="text-xs font-semibold text-muted-foreground">Langage</span>
        <Select value={langage} onValueChange={(l) => l && onChange({ langage: l })}>
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
          language={langage}
          value={code}
          // Au-delà de la limite du serveur, chaque sauvegarde serait refusée : le code est coupé et la limite signalée
          onChange={(valeur) => onChange({ code: (valeur ?? "").slice(0, TAILLE_MAX_CODE) })}
          theme="vs-dark"
          options={{ minimap: { enabled: false }, fontSize: 13, scrollBeyondLastLine: false }}
        />
      </div>
      {code.length >= TAILLE_MAX_CODE && (
        <p className="text-right text-xs font-medium text-destructive">
          Limite de {formatNombre(TAILLE_MAX_CODE)} caractères atteinte : la suite n'est pas enregistrée.
        </p>
      )}

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
          <span className="text-xs font-semibold text-muted-foreground">Cas de test visibles</span>
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
    </>
  )
}
