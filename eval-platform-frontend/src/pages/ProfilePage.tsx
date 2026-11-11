import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import { changerMotDePasse, lireProfil } from "@/lib/profil"
import { messageErreur } from "@/lib/passation"
import type { Role } from "@/types"
import { CheckCircle2, KeyRound, User } from "lucide-react"

const ROLE_LABEL: Record<Role, string> = {
  ETUDIANT:   "Étudiant",
  ENSEIGNANT: "Enseignant",
  ADMIN:      "Administrateur",
}

const ROLE_CLS: Record<Role, string> = {
  ETUDIANT:   "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400",
  ENSEIGNANT: "bg-primary/10 text-primary border-primary/20",
  ADMIN:      "bg-purple-500/10 text-purple-700 border-purple-500/20",
}

/** Longueur minimale, vérifiée aussi par le serveur (qui limite en plus à 72 octets, la limite de BCrypt). */
const LONGUEUR_MIN = 8

interface MdpForm {
  actuel: string
  nouveau: string
  confirmation: string
}

function Champ({ libelle, valeur }: { libelle: string; valeur: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{libelle}</dt>
      <dd className="text-sm font-medium break-words">{valeur}</dd>
    </div>
  )
}

/**
 * Profil de l'utilisateur connecté (tous les rôles). L'identité vient du serveur et n'est pas modifiable ici : elle
 * est gérée par l'administrateur (cahier des charges, gestion des comptes). Seul le mot de passe se change.
 */
export default function ProfilePage() {
  const { data: profil, isPending, isError } = useQuery({ queryKey: ["profil"], queryFn: lireProfil })

  const [mdp, setMdp] = useState<MdpForm>({ actuel: "", nouveau: "", confirmation: "" })
  const [savedMdp, setSavedMdp] = useState(false)
  const [errorMdp, setErrorMdp] = useState<string | null>(null)
  const [savingMdp, setSavingMdp] = useState(false)

  function setMdpField<K extends keyof MdpForm>(key: K, val: string) {
    setMdp(f => ({ ...f, [key]: val }))
    setSavedMdp(false)
    setErrorMdp(null)
  }

  async function handleSaveMdp() {
    if (mdp.nouveau !== mdp.confirmation) {
      setErrorMdp("Les deux mots de passe ne correspondent pas.")
      return
    }
    if (mdp.nouveau.length < LONGUEUR_MIN) {
      setErrorMdp(`Le mot de passe doit contenir au moins ${LONGUEUR_MIN} caractères.`)
      return
    }
    setSavingMdp(true)
    try {
      await changerMotDePasse(mdp.actuel, mdp.nouveau)
      setMdp({ actuel: "", nouveau: "", confirmation: "" })
      setSavedMdp(true)
    } catch (err) {
      // Mot de passe actuel faux (400), trop d'échecs récents (429), nouveau mot de passe refusé : message du serveur
      setErrorMdp(messageErreur(err, "Le mot de passe n'a pas pu être modifié. Vérifiez votre connexion puis réessayez."))
    } finally {
      setSavingMdp(false)
    }
  }

  const initiales = profil ? `${profil.prenom?.[0] ?? ""}${profil.nom?.[0] ?? ""}`.toUpperCase() : "?"

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">

      {/* En-tête */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Mon profil</h1>
        <p className="mt-1 text-muted-foreground">
          Consultez vos informations et changez votre mot de passe.
        </p>
      </div>

      {/* Informations personnelles (lecture seule) */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base font-bold">
            <User className="size-5 text-primary" />
            Informations personnelles
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {isPending ? (
            <p className="text-sm text-muted-foreground">Chargement du profil...</p>
          ) : isError || !profil ? (
            <p role="alert" className="text-sm text-destructive">
              Impossible de charger votre profil. Vérifiez votre connexion puis rechargez la page.
            </p>
          ) : (
            <>
              <div className="flex items-center gap-4">
                <div className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-2xl font-extrabold text-primary">
                  {initiales}
                </div>
                <div className="min-w-0">
                  <p className="text-xl font-bold">
                    {profil.prenom} {profil.nom}
                  </p>
                  <Badge
                    variant="outline"
                    className={`mt-1 rounded-full text-xs font-semibold ${ROLE_CLS[profil.role]}`}
                  >
                    {ROLE_LABEL[profil.role]}
                  </Badge>
                </div>
              </div>

              <dl className="grid gap-4 sm:grid-cols-2">
                <Champ libelle="Adresse email" valeur={profil.email} />
                {profil.numEtudiant && <Champ libelle="Matricule" valeur={profil.numEtudiant} />}
                {profil.promotion && <Champ libelle="Promotion" valeur={profil.promotion} />}
              </dl>

              <p className="text-xs text-muted-foreground">
                Une erreur dans ces informations ? Elles sont gérées par l'administration de l'école : contactez
                l'administrateur de la plateforme pour les faire corriger.
              </p>
            </>
          )}
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
            <Label htmlFor="mdp-actuel">Mot de passe actuel</Label>
            <Input
              id="mdp-actuel"
              type="password"
              autoComplete="current-password"
              value={mdp.actuel}
              onChange={e => setMdpField("actuel", e.target.value)}
              className="rounded-xl"
              placeholder="••••••••"
            />
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="mdp-nouveau">Nouveau mot de passe</Label>
              <Input
                id="mdp-nouveau"
                type="password"
                autoComplete="new-password"
                value={mdp.nouveau}
                onChange={e => setMdpField("nouveau", e.target.value)}
                className="rounded-xl"
                placeholder="••••••••"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="mdp-confirmation">Confirmation</Label>
              <Input
                id="mdp-confirmation"
                type="password"
                autoComplete="new-password"
                value={mdp.confirmation}
                onChange={e => setMdpField("confirmation", e.target.value)}
                className="rounded-xl"
                placeholder="••••••••"
              />
            </div>
          </div>

          {errorMdp && (
            <p role="alert" className="text-sm text-destructive">{errorMdp}</p>
          )}

          <p className="text-[11px] text-muted-foreground">
            Minimum {LONGUEUR_MIN} caractères. Utilisez un mélange de lettres, chiffres et symboles.
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
