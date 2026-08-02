import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import { useAuth } from "@/context/AuthContext"
import { CheckCircle2, KeyRound, User } from "lucide-react"

// ============================================================
// Données mockées — à remplacer par :
// GET  /api/utilisateurs/moi
// PUT  /api/utilisateurs/moi         (infos personnelles)
// PUT  /api/utilisateurs/moi/mdp     (changement mot de passe)
// ============================================================

const ROLE_LABEL: Record<string, string> = {
  ETUDIANT:   "Étudiant",
  ENSEIGNANT: "Enseignant",
  ADMIN:      "Administrateur",
}

const ROLE_CLS: Record<string, string> = {
  ETUDIANT:   "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400",
  ENSEIGNANT: "bg-primary/10 text-primary border-primary/20",
  ADMIN:      "bg-purple-500/10 text-purple-700 border-purple-500/20",
}

interface InfosForm {
  nom: string
  prenom: string
  email: string
}

interface MdpForm {
  actuel: string
  nouveau: string
  confirmation: string
}

export default function ProfilePage() {
  const { user } = useAuth()

  const [infos, setInfos] = useState<InfosForm>({
    nom:    user?.nom    ?? "",
    prenom: user?.prenom ?? "",
    email:  user?.email  ?? "",
  })

  const [mdp, setMdp] = useState<MdpForm>({
    actuel:       "",
    nouveau:      "",
    confirmation: "",
  })

  const [savedInfos, setSavedInfos]   = useState(false)
  const [savedMdp,   setSavedMdp]     = useState(false)
  const [errorMdp,   setErrorMdp]     = useState<string | null>(null)
  const [savingInfos, setSavingInfos] = useState(false)
  const [savingMdp,   setSavingMdp]   = useState(false)

  function setInfo<K extends keyof InfosForm>(key: K, val: string) {
    setInfos(f => ({ ...f, [key]: val }))
    setSavedInfos(false)
  }

  function setMdpField<K extends keyof MdpForm>(key: K, val: string) {
    setMdp(f => ({ ...f, [key]: val }))
    setSavedMdp(false)
    setErrorMdp(null)
  }

  async function handleSaveInfos() {
    setSavingInfos(true)
    try {
      // TODO : await api.put("/utilisateurs/moi", infos)
      await new Promise(r => setTimeout(r, 600))
      setSavedInfos(true)
    } finally {
      setSavingInfos(false)
    }
  }

  async function handleSaveMdp() {
    if (mdp.nouveau !== mdp.confirmation) {
      setErrorMdp("Les deux mots de passe ne correspondent pas.")
      return
    }
    if (mdp.nouveau.length < 8) {
      setErrorMdp("Le mot de passe doit contenir au moins 8 caractères.")
      return
    }
    setSavingMdp(true)
    try {
      // TODO : await api.put("/utilisateurs/moi/mdp", { actuel: mdp.actuel, nouveau: mdp.nouveau })
      await new Promise(r => setTimeout(r, 600))
      setMdp({ actuel: "", nouveau: "", confirmation: "" })
      setSavedMdp(true)
    } finally {
      setSavingMdp(false)
    }
  }

  const initiales = user
    ? `${user.prenom?.[0] ?? ""}${user.nom?.[0] ?? ""}`.toUpperCase()
    : "?"

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">

      {/* En-tête */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Mon profil</h1>
        <p className="mt-1 text-muted-foreground">
          Gérez vos informations personnelles et votre mot de passe.
        </p>
      </div>

      {/* Avatar + rôle */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex items-center gap-4 p-5">
          <div className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-2xl font-extrabold text-primary">
            {initiales}
          </div>
          <div>
            <p className="text-xl font-bold">
              {user?.prenom} {user?.nom}
            </p>
            <p className="text-sm text-muted-foreground">{user?.email}</p>
            <div className="mt-2">
              <Badge
                variant="outline"
                className={`rounded-full text-xs font-semibold ${ROLE_CLS[user?.role ?? "ETUDIANT"]}`}
              >
                {ROLE_LABEL[user?.role ?? "ETUDIANT"]}
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Informations personnelles */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base font-bold">
            <User className="size-5 text-primary" />
            Informations personnelles
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label>Prénom</Label>
              <Input
                value={infos.prenom}
                onChange={e => setInfo("prenom", e.target.value)}
                className="rounded-xl"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Nom</Label>
              <Input
                value={infos.nom}
                onChange={e => setInfo("nom", e.target.value)}
                className="rounded-xl"
              />
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>Adresse email</Label>
            <Input
              type="email"
              value={infos.email}
              onChange={e => setInfo("email", e.target.value)}
              className="rounded-xl"
            />
          </div>

          <div className="flex items-center gap-3">
            <Button
              className="rounded-xl"
              onClick={handleSaveInfos}
              disabled={savingInfos}
            >
              {savingInfos ? "Enregistrement..." : "Enregistrer"}
            </Button>
            {savedInfos && (
              <span className="flex items-center gap-1.5 text-sm text-primary">
                <CheckCircle2 className="size-4" /> Modifications enregistrées
              </span>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Changement de mot de passe */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base font-bold">
            <KeyRound className="size-5 text-primary" />
            Changer le mot de passe
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label>Mot de passe actuel</Label>
            <Input
              type="password"
              value={mdp.actuel}
              onChange={e => setMdpField("actuel", e.target.value)}
              className="rounded-xl"
              placeholder="••••••••"
            />
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label>Nouveau mot de passe</Label>
              <Input
                type="password"
                value={mdp.nouveau}
                onChange={e => setMdpField("nouveau", e.target.value)}
                className="rounded-xl"
                placeholder="••••••••"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Confirmation</Label>
              <Input
                type="password"
                value={mdp.confirmation}
                onChange={e => setMdpField("confirmation", e.target.value)}
                className="rounded-xl"
                placeholder="••••••••"
              />
            </div>
          </div>

          {errorMdp && (
            <p className="text-sm text-destructive">{errorMdp}</p>
          )}

          <p className="text-[11px] text-muted-foreground">
            Minimum 8 caractères. Utilisez un mélange de lettres, chiffres et symboles.
          </p>

          <div className="flex items-center gap-3">
            <Button
              className="rounded-xl"
              onClick={handleSaveMdp}
              disabled={savingMdp || !mdp.actuel || !mdp.nouveau}
            >
              {savingMdp ? "Modification..." : "Modifier le mot de passe"}
            </Button>
            {savedMdp && (
              <span className="flex items-center gap-1.5 text-sm text-primary">
                <CheckCircle2 className="size-4" /> Mot de passe modifié
              </span>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
