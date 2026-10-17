import { useState } from "react"
import { Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { GraduationCap, ArrowLeft, MailCheck, Send } from "lucide-react"

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("")
  const [envoye, setEnvoye] = useState(false)
  const [chargement, setChargement] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)

  async function handleSubmit() {
    setErreur(null)

    if (!email.trim()) {
      setErreur("Veuillez saisir votre adresse email.")
      return
    }

    setChargement(true)
    try {
      // TODO backend : POST /api/auth/mot-de-passe-oublie { email }
      await new Promise((r) => setTimeout(r, 800)) // simulation
      setEnvoye(true)
    } catch {
      setErreur("Une erreur est survenue. Veuillez réessayer.")
    } finally {
      setChargement(false)
    }
  }

  return (
    <div className="flex min-h-svh items-center justify-center bg-muted/30 p-4">
      <div className="flex w-full max-w-md flex-col gap-6">
        {/* Logo */}
        <div className="flex items-center justify-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary/80 text-primary-foreground shadow-sm">
            <GraduationCap className="size-5" />
          </div>
          <span className="text-lg font-bold tracking-tight">Plateforme</span>
        </div>

        {envoye ? (
          /* ---------- État après envoi ---------- */
          <Card className="rounded-2xl border-border/60 shadow-sm">
            <CardContent className="flex flex-col items-center gap-4 py-8 text-center">
              <div className="flex size-14 items-center justify-center rounded-full bg-primary/10">
                <MailCheck className="size-7 text-primary" />
              </div>
              <div className="flex flex-col gap-2">
                <h1 className="text-xl font-bold tracking-tight">
                  Vérifiez votre boîte mail
                </h1>
                <p className="text-sm text-muted-foreground">
                  Si un compte est associé à <strong>{email}</strong>, vous
                  recevrez un lien de réinitialisation dans quelques minutes. Le
                  lien est valable 30 minutes.
                </p>
              </div>
              <Button
                variant="outline"
                className="mt-2"
                render={
                  <Link to="/login">
                    <ArrowLeft className="mr-1 size-4" />
                    Retour à la connexion
                  </Link>
                }
              />
            </CardContent>
          </Card>
        ) : (
          /* ---------- Formulaire ---------- */
          <Card className="rounded-2xl border-border/60 shadow-sm">
            <CardHeader className="gap-2">
              <CardTitle className="text-xl font-bold tracking-tight">
                Mot de passe oublié
              </CardTitle>
              <p className="text-sm text-muted-foreground">
                Saisissez l'adresse email de votre compte. Nous vous enverrons
                un lien pour définir un nouveau mot de passe.
              </p>
            </CardHeader>

            <CardContent className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="email">Adresse email</Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
                  placeholder="prenom.nom@ept.sn"
                  autoComplete="email"
                  className="rounded-xl"
                />
              </div>

              {erreur && (
                <p className="text-sm text-destructive">{erreur}</p>
              )}

              <Button
                className="w-full shadow-sm"
                disabled={chargement}
                onClick={handleSubmit}
              >
                <Send className="mr-1 size-4" />
                {chargement ? "Envoi en cours..." : "Envoyer le lien"}
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
        )}
      </div>
    </div>
  )
}