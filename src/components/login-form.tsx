import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"
import { useAuth } from "@/context/AuthContext"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { accueilPour } from "@/lib/roles"
import { isAxiosError } from "axios"

export function LoginForm({
  className,
  ...props
}: React.ComponentProps<"form">) {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState("")
  const [motDePasse, setMotDePasse] = useState("")
  const [erreur, setErreur] = useState<string | null>(null)
  const [chargement, setChargement] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErreur(null)
    setChargement(true)

    try {
      const loggedUser = await login(email, motDePasse)
      navigate(accueilPour(loggedUser.role))
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 401) {
        setErreur("Email ou mot de passe incorrect.")
      } else if (isAxiosError(err) && !err.response) {
        setErreur("Serveur injoignable. Réessayez dans un instant.")
      } else {
        setErreur("Une erreur est survenue. Veuillez réessayer.")
      }
    } finally {
      setChargement(false)
    }
  }

  return (
    <form
      className={cn("flex flex-col gap-6", className)}
      onSubmit={handleSubmit}
      {...props}
    >
      <div className="flex flex-col items-center gap-2 text-center">
        <h1 className="text-2xl font-bold">Connexion à votre compte</h1>
        <p className="text-muted-foreground text-sm text-balance">
          Entrez votre email pour accéder à votre espace
        </p>
      </div>
      <div className="grid gap-6">
        <div className="grid gap-3">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            placeholder="prenom.nom@ept.sn"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="grid gap-3">
          <div className="flex items-center">
            <Label htmlFor="password">Mot de passe</Label>
            <Link
              to="/forgot-password"
              className="ml-auto text-sm underline-offset-4 hover:underline"
            >
              Mot de passe oublié ?
            </Link>
          </div>
          <Input
            id="password"
            type="password"
            value={motDePasse}
            onChange={(e) => setMotDePasse(e.target.value)}
            required
          />
        </div>

        {erreur && (
          <p className="text-destructive text-sm text-center">{erreur}</p>
        )}

        <Button type="submit" className="w-full" disabled={chargement}>
          {chargement ? "Connexion..." : "Se connecter"}
        </Button>
      </div>
    </form>
  )
}