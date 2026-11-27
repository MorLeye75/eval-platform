import { Textarea } from "@/components/ui/textarea"
import { formatNombre, TAILLE_MAX_TEXTE } from "@/lib/passation"

interface SaisieTexteProps {
  texte: string
  onChange: (texte: string) => void
}

/** Réponse rédigée à une question ouverte, bornée à la taille acceptée par le serveur. */
export default function SaisieTexte({ texte, onChange }: SaisieTexteProps) {
  const nbMots = texte.trim() ? texte.trim().split(/\s+/).length : 0
  return (
    <>
      <Textarea
        value={texte}
        maxLength={TAILLE_MAX_TEXTE}
        onChange={(e) => onChange(e.target.value.slice(0, TAILLE_MAX_TEXTE))}
        placeholder="Rédigez votre réponse ici..."
        className="min-h-56 resize-y rounded-xl text-sm leading-relaxed"
      />
      <p className="text-right text-xs text-muted-foreground">
        {nbMots} mot{nbMots > 1 ? "s" : ""} · {texte.length} caractère{texte.length > 1 ? "s" : ""}
      </p>
      {texte.length >= TAILLE_MAX_TEXTE && (
        <p className="text-right text-xs font-medium text-destructive">
          Limite de {formatNombre(TAILLE_MAX_TEXTE)} caractères atteinte.
        </p>
      )}
    </>
  )
}
