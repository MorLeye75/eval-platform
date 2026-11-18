import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Search, Plus, Pencil, Power, KeyRound, Upload, Download, Trash2 } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import type { Role } from "@/types"
import {
  CLE_COMPTES_ADMIN,
  changerEtatCompte,
  creerCompte,
  creerPromotion,
  csvMotsDePasse,
  importerEtudiants,
  listerComptes,
  modifierCompte,
  reinitialiserMotDePasse,
  renommerPromotion,
  supprimerPromotion,
  type CompteAdminApi,
  type ImportApi,
} from "@/lib/adminComptes"
import { CLE_PROMOTIONS, listerPromotions } from "@/lib/evaluationsEnseignant"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Gestion des comptes (DS-16) : liste, création, modification, désactivation, mot de passe provisoire, import CSV.
// Un compte n'est jamais supprimé (ses copies et ses évaluations y sont rattachées) : il est désactivé.
// ============================================================

const ROLE_LABEL: Record<Role, string> = { ETUDIANT: "Étudiant", ENSEIGNANT: "Enseignant", ADMIN: "Admin" }
const ROLE_CLS: Record<Role, string> = {
  ETUDIANT:   "bg-blue-500/10 text-blue-700 dark:text-blue-400",
  ENSEIGNANT: "bg-primary/10 text-primary",
  ADMIN:      "bg-purple-500/10 text-purple-700 dark:text-purple-400",
}

function formatDate(iso: string | null) {
  if (!iso) return "—"
  const diffMin = Math.round((new Date(iso).getTime() - Date.now()) / 60000)
  const rtf = new Intl.RelativeTimeFormat("fr", { numeric: "auto" })
  if (Math.abs(diffMin) < 60) return rtf.format(diffMin, "minute")
  if (Math.abs(diffMin) < 60 * 24) return rtf.format(Math.round(diffMin / 60), "hour")
  if (Math.abs(diffMin) < 60 * 24 * 30) return rtf.format(Math.round(diffMin / 60 / 24), "day")
  return rtf.format(Math.round(diffMin / 60 / 24 / 30), "month")
}

interface Formulaire {
  id: number | null // null = création
  role: Role
  nom: string
  prenom: string
  email: string
  promotionId: string
  numEtudiant: string
  matricule: string
}

const VIDE: Formulaire = { id: null, role: "ETUDIANT", nom: "", prenom: "", email: "", promotionId: "", numEtudiant: "", matricule: "" }

function telecharger(nom: string, contenu: string) {
  const url = URL.createObjectURL(new Blob(["﻿" + contenu], { type: "text/csv;charset=utf-8" }))
  const lien = document.createElement("a")
  lien.href = url
  lien.download = nom
  lien.click()
  URL.revokeObjectURL(url)
}

export default function GestionComptes() {
  const [tab, setTab]         = useState<"tous" | Role>("tous")
  const [query, setQuery]     = useState("")
  const [form, setForm]       = useState<Formulaire | null>(null)
  const [erreur, setErreur]   = useState<string | null>(null)
  const [info, setInfo]       = useState<string | null>(null)
  // Mot de passe provisoire : affiché une seule fois, jamais stocké
  const [provisoire, setProvisoire] = useState<{ email: string; motDePasse: string } | null>(null)
  const [fichier, setFichier] = useState<File | null>(null)
  const [resultat, setResultat] = useState<ImportApi | null>(null)
  const [promo, setPromo]     = useState<{ id: number | null; nom: string; annee: string }>({ id: null, nom: "", annee: "" })
  const queryClient = useQueryClient()

  const comptes = useQuery({ queryKey: CLE_COMPTES_ADMIN, queryFn: listerComptes })
  const promotions = useQuery({ queryKey: CLE_PROMOTIONS, queryFn: listerPromotions })

  const apres = () => queryClient.invalidateQueries({ queryKey: CLE_COMPTES_ADMIN })
  const echec = (defaut: string) => (e: unknown) => { setInfo(null); setErreur(messageErreur(e, defaut)) }

  const enregistrement = useMutation({
    mutationFn: async (f: Formulaire) => {
      const corps = {
        nom: f.nom, prenom: f.prenom, email: f.email,
        promotionId: f.role === "ETUDIANT" && f.promotionId ? Number(f.promotionId) : null,
        numEtudiant: f.role === "ETUDIANT" ? f.numEtudiant : null,
        matricule: f.role === "ENSEIGNANT" ? f.matricule : null,
      }
      return f.id === null ? creerCompte({ ...corps, role: f.role }) : { utilisateur: await modifierCompte(f.id, corps), motDePasseProvisoire: null }
    },
    onSuccess: (r) => {
      setErreur(null)
      setForm(null)
      if (r.motDePasseProvisoire) {
        setProvisoire({ email: r.utilisateur.email, motDePasse: r.motDePasseProvisoire })
        setInfo("Compte créé.")
      } else {
        setInfo("Compte modifié.")
      }
    },
    onError: echec("L'enregistrement du compte a échoué."),
    onSettled: apres,
  })

  const etat = useMutation({
    mutationFn: ({ id, actif }: { id: number; actif: boolean }) => changerEtatCompte(id, actif),
    onSuccess: (c) => { setErreur(null); setInfo(c.actif ? "Compte réactivé." : "Compte désactivé : il ne peut plus se connecter.") },
    onError: echec("Le changement d'état a échoué."),
    onSettled: apres,
  })

  const reinitialisation = useMutation({
    mutationFn: ({ id }: { id: number; email: string }) => reinitialiserMotDePasse(id),
    onSuccess: (r, v) => { setErreur(null); setInfo(null); setProvisoire({ email: v.email, motDePasse: r.motDePasseProvisoire }) },
    onError: echec("La réinitialisation a échoué."),
  })

  const importation = useMutation({
    mutationFn: (f: File) => importerEtudiants(f),
    onSuccess: (r) => { setErreur(null); setResultat(r); if (r.importe) setFichier(null) },
    onError: echec("L'import a échoué."),
    onSettled: apres,
  })

  const nouvellePromotion = useMutation({
    mutationFn: () => promo.id === null
      ? creerPromotion(promo.nom.trim(), promo.annee.trim())
      : renommerPromotion(promo.id, promo.nom.trim(), promo.annee.trim()),
    onSuccess: (p) => {
      setErreur(null)
      setInfo(`Promotion « ${p.nom} » ${promo.id === null ? "créée" : "modifiée"}.`)
      setPromo({ id: null, nom: "", annee: "" })
    },
    onError: echec("L'enregistrement de la promotion a échoué."),
    onSettled: () => { queryClient.invalidateQueries({ queryKey: CLE_PROMOTIONS }); apres() },
  })

  const suppressionPromotion = useMutation({
    mutationFn: (id: number) => supprimerPromotion(id),
    onSuccess: () => { setErreur(null); setInfo("Promotion supprimée."); setPromo({ id: null, nom: "", annee: "" }) },
    onError: echec("La suppression de la promotion a échoué."),
    onSettled: () => queryClient.invalidateQueries({ queryKey: CLE_PROMOTIONS }),
  })

  const q = query.trim().toLowerCase()
  const filtered = (comptes.data ?? []).filter((u) =>
    (tab === "tous" || u.role === tab) &&
    (q === "" || u.nom.toLowerCase().includes(q) || u.prenom.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)))

  function modifier(u: CompteAdminApi) {
    setProvisoire(null)
    setForm({
      id: u.id, role: u.role, nom: u.nom, prenom: u.prenom, email: u.email,
      promotionId: u.promotionId ? String(u.promotionId) : "", numEtudiant: u.numEtudiant ?? "", matricule: u.matricule ?? "",
    })
  }

  const champ = (cle: keyof Formulaire) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => (f ? { ...f, [cle]: e.target.value } : f))

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Gestion des comptes</h1>
          <p className="mt-1 text-muted-foreground">
            Créez, modifiez ou désactivez les comptes utilisateurs. Un compte désactivé ne peut plus se connecter ; ses données sont conservées.
          </p>
        </div>
        <Button className="rounded-xl" onClick={() => { setProvisoire(null); setForm({ ...VIDE }) }}>
          <Plus className="size-4" /> Nouveau compte
        </Button>
      </div>

      {info && <p role="status" className="rounded-xl bg-primary/10 px-4 py-2.5 text-sm text-primary">{info}</p>}
      {erreur && <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{erreur}</p>}

      {provisoire && (
        <div role="status" className="flex flex-col gap-1 rounded-xl border border-primary/30 bg-primary/5 px-4 py-3 text-sm">
          <span>Mot de passe provisoire de <strong>{provisoire.email}</strong> :</span>
          <code className="w-fit rounded-lg bg-background px-3 py-1.5 text-base font-bold tracking-wider">{provisoire.motDePasse}</code>
          <span className="text-xs text-muted-foreground">Affiché une seule fois : transmettez-le à l'utilisateur, qui le changera depuis son profil.</span>
          <Button variant="outline" size="sm" className="mt-1 w-fit rounded-lg" onClick={() => setProvisoire(null)}>Masquer</Button>
        </div>
      )}

      {form && (
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardContent className="flex flex-col gap-4 p-5">
            <h2 className="text-lg font-bold">{form.id === null ? "Nouveau compte" : "Modifier le compte"}</h2>
            <form
              className="grid gap-4 sm:grid-cols-2"
              onSubmit={(e) => { e.preventDefault(); enregistrement.mutate(form) }}
            >
              {form.id === null && (
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="compte-role" className="text-sm font-medium">Rôle</label>
                  <select id="compte-role" value={form.role} onChange={champ("role")}
                    className="h-9 rounded-lg border border-input bg-background px-3 text-sm">
                    <option value="ETUDIANT">Étudiant</option>
                    <option value="ENSEIGNANT">Enseignant</option>
                    <option value="ADMIN">Administrateur</option>
                  </select>
                </div>
              )}
              <div className="flex flex-col gap-1.5">
                <label htmlFor="compte-nom" className="text-sm font-medium">Nom</label>
                <Input id="compte-nom" required value={form.nom} onChange={champ("nom")} className="rounded-lg" />
              </div>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="compte-prenom" className="text-sm font-medium">Prénom</label>
                <Input id="compte-prenom" required value={form.prenom} onChange={champ("prenom")} className="rounded-lg" />
              </div>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="compte-email" className="text-sm font-medium">E-mail</label>
                <Input id="compte-email" type="email" required value={form.email} onChange={champ("email")} className="rounded-lg" />
              </div>
              {form.role === "ETUDIANT" && (
                <>
                  <div className="flex flex-col gap-1.5">
                    <label htmlFor="compte-promotion" className="text-sm font-medium">Promotion</label>
                    <select id="compte-promotion" required value={form.promotionId} onChange={champ("promotionId")}
                      className="h-9 rounded-lg border border-input bg-background px-3 text-sm">
                      <option value="">Choisir…</option>
                      {(promotions.data ?? []).map((p) => <option key={p.id} value={p.id}>{p.nom} ({p.annee})</option>)}
                    </select>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <label htmlFor="compte-numero" className="text-sm font-medium">Numéro d'étudiant</label>
                    <Input id="compte-numero" required value={form.numEtudiant} onChange={champ("numEtudiant")} className="rounded-lg" />
                  </div>
                </>
              )}
              {form.role === "ENSEIGNANT" && (
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="compte-matricule" className="text-sm font-medium">Matricule (facultatif)</label>
                  <Input id="compte-matricule" value={form.matricule} onChange={champ("matricule")} className="rounded-lg" />
                </div>
              )}
              <div className="flex gap-2 sm:col-span-2">
                <Button type="submit" className="rounded-xl" disabled={enregistrement.isPending}>
                  {form.id === null ? "Créer le compte" : "Enregistrer"}
                </Button>
                <Button type="button" variant="outline" className="rounded-xl" onClick={() => setForm(null)}>Annuler</Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Barre outils */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Rechercher un compte"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher par nom ou email..."
            className="rounded-xl pl-9"
          />
        </div>
        <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)}>
          <TabsList className="rounded-xl">
            <TabsTrigger value="tous">Tous</TabsTrigger>
            <TabsTrigger value="ETUDIANT">Étudiants</TabsTrigger>
            <TabsTrigger value="ENSEIGNANT">Enseignants</TabsTrigger>
            <TabsTrigger value="ADMIN">Admins</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {/* Table */}
      {comptes.isError ? (
        <p role="alert" className="text-sm text-destructive">{messageErreur(comptes.error, "Impossible de charger les comptes.")}</p>
      ) : comptes.isLoading ? (
        <p className="text-sm text-muted-foreground">Chargement…</p>
      ) : (
        <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Utilisateur</TableHead>
                  <TableHead>Rôle</TableHead>
                  <TableHead>Statut</TableHead>
                  <TableHead>Dernière connexion</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map((u) => {
                  const nomComplet = `${u.prenom} ${u.nom}`
                  return (
                    <TableRow key={u.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                            {`${u.prenom[0] ?? ""}${u.nom[0] ?? ""}`.toUpperCase()}
                          </div>
                          <div>
                            <div className="font-semibold">{nomComplet}</div>
                            <div className="text-[11px] text-muted-foreground">
                              {u.email}{u.promotion ? ` · ${u.promotion}` : ""}
                            </div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={`rounded-full text-[11px] font-semibold ${ROLE_CLS[u.role]}`}>
                          {ROLE_LABEL[u.role]}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant="outline"
                          className={`inline-flex items-center gap-1.5 rounded-full text-[11px] font-semibold ${u.actif ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground"}`}
                        >
                          <span className="size-1.5 rounded-full bg-current" />
                          {u.actif ? "Actif" : "Désactivé"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">{formatDate(u.derniereConnexion)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" aria-label={`Modifier : ${nomComplet}`} onClick={() => modifier(u)}>
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button variant="ghost" size="icon" aria-label={`Réinitialiser le mot de passe : ${nomComplet}`}
                            disabled={reinitialisation.isPending}
                            onClick={() => {
                              if (window.confirm(`Générer un nouveau mot de passe provisoire pour ${nomComplet} ? L'ancien ne fonctionnera plus.`)) {
                                reinitialisation.mutate({ id: u.id, email: u.email })
                              }
                            }}>
                            <KeyRound className="size-3.5" />
                          </Button>
                          <Button variant="ghost" size="icon"
                            aria-label={`${u.actif ? "Désactiver" : "Réactiver"} : ${nomComplet}`}
                            disabled={etat.isPending}
                            onClick={() => {
                              if (!u.actif || window.confirm(`Désactiver le compte de ${nomComplet} ? Il ne pourra plus se connecter.`)) {
                                etat.mutate({ id: u.id, actif: !u.actif })
                              }
                            }}>
                            <Power className="size-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
      <p className="text-xs text-muted-foreground">{filtered.length} sur {comptes.data?.length ?? 0} comptes</p>

      {/* Promotions et import CSV */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-col gap-4 p-5">
          <h2 className="text-lg font-bold">Importer des étudiants (CSV)</h2>
          <p className="text-sm text-muted-foreground">
            Colonnes, dans cet ordre : <code>nom;prenom;email;numEtudiant;promotion</code> (500 lignes au plus). Tout ou rien : à la moindre
            erreur, aucun compte n'est créé et les lignes à corriger sont listées.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <label htmlFor="import-fichier" className="text-sm font-medium">Fichier :</label>
            <input id="import-fichier" type="file" accept=".csv,text/csv"
              onChange={(e) => { setFichier(e.target.files?.[0] ?? null); setResultat(null) }} className="text-sm" />
            <Button className="rounded-xl" disabled={!fichier || importation.isPending}
              onClick={() => fichier && importation.mutate(fichier)}>
              <Upload className="size-4" /> Importer
            </Button>
          </div>

          {resultat && !resultat.importe && (
            <div role="alert" className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
              <p className="font-semibold">Rien n'a été importé : {resultat.erreurs.length} ligne{resultat.erreurs.length > 1 ? "s" : ""} à corriger.</p>
              <ul className="mt-1 list-disc pl-5">
                {resultat.erreurs.map((er) => <li key={`${er.ligne}-${er.message}`}>Ligne {er.ligne} : {er.message}</li>)}
              </ul>
            </div>
          )}
          {resultat?.importe && (
            <div role="status" className="flex flex-wrap items-center gap-3 rounded-xl bg-primary/10 px-4 py-3 text-sm text-primary">
              <span>{resultat.nbCrees} étudiant{resultat.nbCrees > 1 ? "s" : ""} importé{resultat.nbCrees > 1 ? "s" : ""}.</span>
              <Button variant="outline" size="sm" className="rounded-lg"
                onClick={() => telecharger("mots-de-passe-provisoires.csv", csvMotsDePasse(resultat.comptes))}>
                <Download className="size-4" /> Télécharger les mots de passe provisoires
              </Button>
              <span className="text-xs">Disponibles une seule fois : le serveur ne les conserve pas.</span>
            </div>
          )}

          <hr className="border-border" />
          <h3 className="font-semibold">Promotions</h3>
          {(promotions.data ?? []).length > 0 && (
            <ul className="flex flex-col divide-y divide-border rounded-xl border border-border/60 text-sm">
              {(promotions.data ?? []).map((p) => (
                <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                  <span>{p.nom} ({p.annee}) · {p.nbEtudiants} étudiant{p.nbEtudiants > 1 ? "s" : ""}</span>
                  <span className="flex gap-1">
                    <Button variant="ghost" size="icon" className="size-8" aria-label={`Renommer la promotion ${p.nom}`}
                      onClick={() => setPromo({ id: p.id, nom: p.nom, annee: p.annee })}><Pencil className="size-4" /></Button>
                    <Button variant="ghost" size="icon" className="size-8 text-destructive" aria-label={`Supprimer la promotion ${p.nom}`}
                      disabled={suppressionPromotion.isPending}
                      onClick={() => { if (window.confirm(`Supprimer la promotion « ${p.nom} » ? Possible seulement si elle est vide.`)) suppressionPromotion.mutate(p.id) }}>
                      <Trash2 className="size-4" /></Button>
                  </span>
                </li>
              ))}
            </ul>
          )}
          <h3 className="font-semibold">{promo.id === null ? "Nouvelle promotion" : "Renommer la promotion"}</h3>
          <form className="flex flex-wrap items-end gap-3" onSubmit={(e) => { e.preventDefault(); nouvellePromotion.mutate() }}>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="promotion-nom" className="text-sm font-medium">Nom de la promotion</label>
              <Input id="promotion-nom" required value={promo.nom} onChange={(e) => setPromo((p) => ({ ...p, nom: e.target.value }))} className="rounded-lg" />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="promotion-annee" className="text-sm font-medium">Année de la promotion</label>
              <Input id="promotion-annee" required placeholder="2026-2027" value={promo.annee}
                onChange={(e) => setPromo((p) => ({ ...p, annee: e.target.value }))} className="rounded-lg" />
            </div>
            <Button type="submit" variant="outline" className="rounded-xl" disabled={nouvellePromotion.isPending}>{promo.id === null ? "Créer la promotion" : "Enregistrer la promotion"}</Button>
            {promo.id !== null && (
              <Button type="button" variant="ghost" className="rounded-xl" onClick={() => setPromo({ id: null, nom: "", annee: "" })}>Annuler</Button>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
