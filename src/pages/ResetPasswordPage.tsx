import { useState, useEffect } from "react"
import { useParams, useNavigate, Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  GraduationCap,
  ArrowLeft,
  CircleCheck,
  CircleX,
  Eye,
  EyeOff,
  ShieldCheck,
} from "lucide-react"

const LONGUEUR_MIN = 8

export default function ResetPasswordPage() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()

  const [motDePasse, setMotDePasse] = useState("")
  const [confirmation, setConfirmation] = useState("")
  const [visible, setVisible] = useState(false)
  const [chargement, setChargement] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [succes, setSucces] = useState(false)

  // Redirection après succès, annulée si l'utilisateur quitte la page
  useEffect(() => {
    if (!succes) return
    const t = setTimeout(() => navigate("/login"), 2500)
    return () => clearTimeout(t)
  }, [succes, navigate])

  // Règles de validation affichées à l'utilisateur
  const regles = [
    { libelle: `Au moins ${LONGUEUR_MIN} caractères`, valide: motDePasse.length >= LONGUEUR_MIN },
    { libelle: "Une lettre majuscule", valide: /[A-Z]/.test(motDePasse) },
    { libelle: "Un chiffre", valide: /[0-9]/.test(motDePasse) },
  ]

  const toutesValides = regles.every((r) => r.valide)
  const correspondent = motDePasse.length > 0 && motDePasse === confirmation

  async function handleSubmit() {
    setErreur(null)

    if (!toutesValides) {
      setErreur("Le mot de passe ne respecte pas les critères requis.")
      return
    }
    if (!correspondent) {
      setErreur("Les deux mots de passe ne correspondent pas.")
      return
    }

    setChargement(true)
    try {
      // TODO backend : POST /api/auth/reinitialiser { token, motDePasse }
      await new Promise((r) => setTimeout(r, 800)) // simulation
      setSucces(true)
    } catch {
      setErreur("Le lien est invalide ou a expiré. Veuillez refaire une demande.")
    } finally {
      setChargement(false)
    }
  }

  const logo = (
    <div className="flex items-center justify-center gap-3">
      <div className="flex size-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary/80 text-primary-foreground shadow-sm">
        <GraduationCap className="size-5" />
      </div>
      <span className="text-lg font-bold tracking-tight">Plateforme</span>
    </div>
  )

  // ---------- Cas : lien sans token ----------
  if (!token) {
    return (
      <div className="flex min-h-svh items-center justify-center bg-muted/30 p-4">
        <div className="flex w-full max-w-md flex-col gap-6">
          {logo}
          <Card className="rounded-2xl border-border/60 shadow-sm">
            <CardContent className="flex flex-col items-center gap-4 py-8 text-center">
              <div className="flex size-14 items-center justify-center rounded-full bg-destructive/10">
                <CircleX className="size-7 text-destructive" />
              </div>
              <div className="flex flex-col gap-2">
                <h1 className="text-xl font-bold tracking-tight">
                  Lien invalide
                </h1>
                <p className="text-sm text-muted-foreground">
                  Ce lien de réinitialisation est incomplet ou a expiré.
                  Veuillez refaire une demande.
                </p>
              </div>
              <Button
                className="mt-2"
                render={<Link to="/forgot-password">Nouvelle demande</Link>}
              />
            </CardContent>
          </Card>
        </div>
      </div>
    )
  }

  // ---------- Cas : succès ----------
  if (succes) {
    return (
      <div className="flex min-h-svh items-center justify-center bg-muted/30 p-4">
        <div className="flex w-full max-w-md flex-col gap-6">
          {logo}
          <Card className="rounded-2xl border-border/60 shadow-sm">
            <CardContent className="flex flex-col items-center gap-4 py-8 text-center">
              <div className="flex size-14 items-center justify-center rounded-full bg-primary/10">
                <CircleCheck className="size-7 text-primary" />
              </div>
              <div className="flex flex-col gap-2">
                <h1 className="text-xl font-bold tracking-tight">
                  Mot de passe modifié
                </h1>
                <p className="text-sm text-muted-foreground">
                  Vous pouvez maintenant vous connecter avec votre nouveau mot
                  de passe. Redirection en cours...
                </p>
              </div>
              <Button
                variant="outline"
                className="mt-2"
                render={<Link to="/login">Se connecter</Link>}
              />
            </CardContent>
          </Card>
        </div>
      </div>
    )
  }

  // ---------- Cas : formulaire ----------
  return (
    <div className="flex min-h-svh items-center justify-center bg-muted/30 p-4">
      <div className="flex w-full max-w-md flex-col gap-6">
        {logo}

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="gap-2">
            <CardTitle className="text-xl font-bold tracking-tight">
              Nouveau mot de passe
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              Choisissez un mot de passe sécurisé pour votre compte.
            </p>
          </CardHeader>

          <CardContent className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="mdp">Mot de passe</Label>
              <div className="relative">
                <Input
                  id="mdp"
                  type={visible ? "text" : "password"}
                  value={motDePasse}
                  onChange={(e) => setMotDePasse(e.target.value)}
                  autoComplete="new-password"
                  className="rounded-xl pr-10"
                />
                <button
                  type="button"
                  onClick={() => setVisible((v) => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                  aria-label={visible ? "Masquer" : "Afficher"}
                >
                  {visible ? (
                    <EyeOff className="size-4" />
                  ) : (
                    <Eye className="size-4" />
                  )}
                </button>
              </div>
            </div>

            {/* Critères de sécurité */}
            <div className="flex flex-col gap-1.5 rounded-xl bg-muted/60 p-3">
              <span className="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
                <ShieldCheck className="size-3.5" />
                Critères requis
              </span>
              {regles.map((r) => (
                <span
                  key={r.libelle}
                  className={`flex items-center gap-2 text-xs ${
                    r.valide ? "text-primary" : "text-muted-foreground"
                  }`}
                >
                  {r.valide ? (
                    <CircleCheck className="size-3.5 shrink-0" />
                  ) : (
                    <span className="size-3.5 shrink-0 rounded-full border border-current" />
                  )}
                  {r.libelle}
                </span>
              ))}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="confirmation">Confirmer le mot de passe</Label>
              <Input
                id="confirmation"
                type={visible ? "text" : "password"}
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
                autoComplete="new-password"
                className="rounded-xl"
              />
              {confirmation.length > 0 && !correspondent && (
                <p className="text-xs text-destructive">
                  Les deux mots de passe ne correspondent pas.
                </p>
              )}
            </div>

            {erreur && <p className="text-sm text-destructive">{erreur}</p>}

            <Button
              className="w-full shadow-sm"
              disabled={chargement || !toutesValides || !correspondent}
              onClick={handleSubmit}
            >
              <CircleCheck className="mr-1 size-4" />
              {chargement ? "Modification..." : "Modifier le mot de passe"}
            </Button>

            <Button
              variant="ghost"
              size="sm"
              className="self-center text-muted-foreground"
              render={
                <Link to="/login">
                  <ArrowLeft className="mr-1 size-4" />
                  Retour à la connexion
                </Link>
              }
            />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}