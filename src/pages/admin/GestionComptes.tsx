import { useState } from "react"
import { Search, Plus, Pencil, Trash2 } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import type { UtilisateurAdmin } from "@/types"

/* ---------------------------------------------------------------
   Données mockées — à remplacer par :
   const { data } = useQuery({ queryKey: ["admin-users"], queryFn: () => api.get("/admin/utilisateurs") })
--------------------------------------------------------------- */
const MOCK_USERS: UtilisateurAdmin[] = [
  { id: 1, nom: "Sarr",   prenom: "Fatou",    email: "fatou.sarr@ept.sn",   role: "ENSEIGNANT", actif: true,  dateCreation: "2026-01-10T08:00:00", derniereConnexion: "2026-07-20T14:55:00" },
  { id: 2, nom: "Diop",   prenom: "Moussa",   email: "moussa.diop@ept.sn",  role: "ETUDIANT",   actif: true,  dateCreation: "2026-01-12T08:00:00", derniereConnexion: "2026-07-20T13:40:00" },
  { id: 3, nom: "Ndiaye", prenom: "Awa",      email: "awa.ndiaye@ept.sn",   role: "ETUDIANT",   actif: true,  dateCreation: "2026-01-12T08:00:00", derniereConnexion: "2026-07-20T11:00:00" },
  { id: 4, nom: "Fall",   prenom: "Ibrahima", email: "i.fall@ept.sn",       role: "ENSEIGNANT", actif: false, dateCreation: "2026-01-08T08:00:00", derniereConnexion: "2026-07-08T09:00:00" },
  { id: 5, nom: "Mbaye",  prenom: "Sokhna",   email: "sokhna.mbaye@ept.sn", role: "ADMIN",      actif: true,  dateCreation: "2026-01-01T08:00:00", derniereConnexion: "2026-07-20T12:00:00" },
  { id: 6, nom: "Ba",     prenom: "Cheikh",   email: "cheikh.ba@ept.sn",    role: "ETUDIANT",   actif: true,  dateCreation: "2026-01-15T08:00:00", derniereConnexion: "2026-07-19T10:00:00" },
  { id: 7, nom: "Sy",     prenom: "Mariama",  email: "mariama.sy@ept.sn",   role: "ETUDIANT",   actif: true,  dateCreation: "2026-01-15T08:00:00", derniereConnexion: "2026-07-20T14:30:00" },
]

const ROLE_LABEL: Record<string, string> = {
  ETUDIANT:   "Étudiant",
  ENSEIGNANT: "Enseignant",
  ADMIN:      "Admin",
}

const ROLE_CLS: Record<string, string> = {
  ETUDIANT:   "bg-blue-500/10 text-blue-700 dark:text-blue-400",
  ENSEIGNANT: "bg-primary/10 text-primary",
  ADMIN:      "bg-purple-500/10 text-purple-700 dark:text-purple-400",
}

function getInitiales(u: UtilisateurAdmin) {
  return `${u.prenom[0] ?? ""}${u.nom[0] ?? ""}`.toUpperCase()
}

function formatDate(iso: string | null) {
  if (!iso) return "—"
  const diffMs = new Date(iso).getTime() - Date.now()
  const diffSec = Math.round(diffMs / 1000)
  const diffMin = Math.round(diffSec / 60)
  const diffHour = Math.round(diffMin / 60)
  const diffDay = Math.round(diffHour / 24)
  const diffMonth = Math.round(diffDay / 30)

  const rtf = new Intl.RelativeTimeFormat("fr", { numeric: "auto" })
  if (Math.abs(diffMin) < 60) return rtf.format(diffMin, "minute")
  if (Math.abs(diffHour) < 24) return rtf.format(diffHour, "hour")
  if (Math.abs(diffDay) < 30) return rtf.format(diffDay, "day")
  return rtf.format(diffMonth, "month")
}

const TABS = ["tous", "etudiants", "enseignants", "admins"] as const

export default function GestionComptes() {
  const [tab, setTab]     = useState<(typeof TABS)[number]>("tous")
  const [query, setQuery] = useState("")

  const filtered = MOCK_USERS.filter((u) => {
    const matchTab =
      tab === "tous"         ||
      (tab === "etudiants"   && u.role === "ETUDIANT")   ||
      (tab === "enseignants" && u.role === "ENSEIGNANT")  ||
      (tab === "admins"      && u.role === "ADMIN")
    const q = query.toLowerCase()
    const matchQuery =
      u.nom.toLowerCase().includes(q)    ||
      u.prenom.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q)
    return matchTab && matchQuery
  })

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Gestion des comptes</h1>
          <p className="mt-1 text-muted-foreground">
            Créez, modifiez ou désactivez les comptes utilisateurs.
          </p>
        </div>
        <Button className="rounded-xl">
          <Plus className="size-4" /> Nouveau compte
        </Button>
      </div>

      {/* Barre outils */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher par nom ou email..."
            className="rounded-xl pl-9"
          />
        </div>
        <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)}>
          <TabsList className="rounded-xl">
            <TabsTrigger value="tous">Tous</TabsTrigger>
            <TabsTrigger value="etudiants">Étudiants</TabsTrigger>
            <TabsTrigger value="enseignants">Enseignants</TabsTrigger>
            <TabsTrigger value="admins">Admins</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {/* Table */}
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
              {filtered.map((u) => (
                <TableRow key={u.id}>
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                        {getInitiales(u)}
                      </div>
                      <div>
                        <div className="font-semibold">{u.prenom} {u.nom}</div>
                        <div className="text-[11px] text-muted-foreground">{u.email}</div>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className={`rounded-full text-[11px] font-semibold ${ROLE_CLS[u.role]}`}>
                      {ROLE_LABEL[u.role]}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {/* Uniquement Actif / Désactivé — actif: boolean */}
                    <Badge
                      variant="outline"
                      className={`inline-flex items-center gap-1.5 rounded-full text-[11px] font-semibold ${
                        u.actif
                          ? "bg-primary/10 text-primary"
                          : "bg-muted text-muted-foreground"
                      }`}
                    >
                      <span className="size-1.5 rounded-full bg-current" />
                      {u.actif ? "Actif" : "Désactivé"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {formatDate(u.derniereConnexion)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <button className="inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
                        <Pencil className="size-3.5" />
                      </button>
                      <button className="inline-flex size-8 items-center justify-center rounded-lg border border-border bg-background text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive">
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Pagination */}
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>{filtered.length} sur {MOCK_USERS.length} utilisateurs</span>
        <div className="flex gap-1.5">
          <button className="rounded-lg border border-border bg-background px-3 py-1.5 font-semibold transition-colors hover:bg-muted">
            ← Précédent
          </button>
          <button className="rounded-lg border border-border bg-background px-3 py-1.5 font-semibold transition-colors hover:bg-muted">
            Suivant →
          </button>
        </div>
      </div>
    </div>
  )
}
