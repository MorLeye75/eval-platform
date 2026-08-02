import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { CheckCircle2, XCircle, ChevronDown, ChevronUp, Bot, User } from "lucide-react"
import type { StatutProposition } from "@/types"

// ============================================================
// Données mockées — à remplacer par :
// const { data } = useQuery({
//   queryKey: ["corrections-ia"],
//   queryFn: () => api.get("/enseignant/corrections-ia")
// })
// Validation : PUT /api/enseignant/corrections-ia/{id}/valider { noteFinale }
// Rejet     : PUT /api/enseignant/corrections-ia/{id}/rejeter { noteFinale }
// ============================================================

interface CorrectionIA {
  id: number
  evaluation: string
  etudiant: { nom: string; prenom: string }
  question: string
  reponse: string
  noteProposeeIA: number
  pointsMax: number
  justificationIA: string
  statut: StatutProposition
}

const MOCK: CorrectionIA[] = [
  {
    id: 1,
    evaluation: "Génie logiciel — questions ouvertes",
    etudiant: { nom: "Diop", prenom: "Moussa" },
    question: "Expliquez la différence entre agrégation et composition en UML. Illustrez par un exemple.",
    reponse: "L'agrégation est une relation faible où l'objet peut exister sans son conteneur (ex: un étudiant peut exister sans promotion). La composition est forte : si le conteneur est détruit, les composants le sont aussi (ex: les pages d'un livre).",
    noteProposeeIA: 4.5,
    pointsMax: 5,
    justificationIA: "Réponse complète couvrant les deux notions avec des exemples pertinents. Légère imprécision sur la notion de cycle de vie partagé.",
    statut: "EN_ATTENTE",
  },
  {
    id: 2,
    evaluation: "Génie logiciel — questions ouvertes",
    etudiant: { nom: "Ndiaye", prenom: "Awa" },
    question: "Quels sont les avantages d'une architecture client-serveur découplée ?",
    reponse: "Le découplage permet de modifier le frontend sans toucher au backend. Cela facilite aussi le travail en équipe et la scalabilité.",
    noteProposeeIA: 3.0,
    pointsMax: 5,
    justificationIA: "Réponse partielle. Deux avantages identifiés correctement mais manque de profondeur et d'un troisième argument demandé par la consigne.",
    statut: "EN_ATTENTE",
  },
  {
    id: 3,
    evaluation: "Réseaux — questions ouvertes",
    etudiant: { nom: "Ba", prenom: "Cheikh" },
    question: "Expliquez le rôle du protocole TCP dans la couche transport.",
    reponse: "TCP assure la livraison fiable des paquets avec accusé de réception et retransmission en cas de perte.",
    noteProposeeIA: 2.5,
    pointsMax: 4,
    justificationIA: "Correct sur la fiabilité et l'accusé de réception. Manque la notion de contrôle de flux et de connexion établie (handshake).",
    statut: "EN_ATTENTE",
  },
  {
    id: 4,
    evaluation: "Génie logiciel — questions ouvertes",
    etudiant: { nom: "Sarr", prenom: "Fatou" },
    question: "Pourquoi utiliser des branches Git en équipe ?",
    reponse: "Les branches permettent de travailler en parallèle sans se bloquer mutuellement. Chaque développeur travaille sur sa feature, puis fait une pull request pour intégrer ses changements après relecture.",
    noteProposeeIA: 5.0,
    pointsMax: 5,
    justificationIA: "Réponse excellente, complète et bien illustrée avec le workflow PR.",
    statut: "VALIDEE",
  },
]

const STATUT_CONFIG: Record<StatutProposition, { label: string; cls: string }> = {
  EN_ATTENTE: { label: "En attente",  cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400" },
  VALIDEE:    { label: "Validée",     cls: "bg-primary/10 text-primary border-primary/20"                              },
  REJETEE:    { label: "Rejetée",     cls: "bg-destructive/10 text-destructive border-destructive/20"                  },
}

export default function ValidationIA() {
  const [corrections, setCorrections] = useState<CorrectionIA[]>(MOCK)
  const [tab,         setTab]         = useState("EN_ATTENTE")
  const [expanded,    setExpanded]    = useState<number | null>(null)
  const [notes,       setNotes]       = useState<Record<number, string>>({})

  const filtered = corrections.filter(
    (c) => tab === "toutes" || c.statut === tab
  )

  const nbEnAttente = corrections.filter((c) => c.statut === "EN_ATTENTE").length

  function valider(id: number) {
    const note = parseFloat(notes[id] ?? "")
    const correction = corrections.find((c) => c.id === id)
    if (!correction) return
    const noteFinale = isNaN(note) ? correction.noteProposeeIA : note
    // TODO : await api.put(`/enseignant/corrections-ia/${id}/valider`, { noteFinale })
    setCorrections((cs) => cs.map((c) => c.id === id ? { ...c, statut: "VALIDEE", noteProposeeIA: noteFinale } : c))
    setExpanded(null)
  }

  function rejeter(id: number) {
    const note = parseFloat(notes[id] ?? "")
    const correction = corrections.find((c) => c.id === id)
    if (!correction) return
    const noteFinale = isNaN(note) ? 0 : note
    // TODO : await api.put(`/enseignant/corrections-ia/${id}/rejeter`, { noteFinale })
    setCorrections((cs) => cs.map((c) => c.id === id ? { ...c, statut: "REJETEE", noteProposeeIA: noteFinale } : c))
    setExpanded(null)
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Validation des corrections IA</h1>
          <p className="mt-1 text-muted-foreground">
            Vérifiez et validez les notes proposées par l'intelligence artificielle.
          </p>
        </div>
        {nbEnAttente > 0 && (
          <Badge variant="outline" className="rounded-full bg-yellow-500/10 text-yellow-700 border-yellow-500/20 px-3 py-1.5 text-sm font-semibold">
            {nbEnAttente} en attente
          </Badge>
        )}
      </div>

      {/* Onglets */}
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="rounded-xl">
          <TabsTrigger value="EN_ATTENTE">En attente ({nbEnAttente})</TabsTrigger>
          <TabsTrigger value="VALIDEE">Validées</TabsTrigger>
          <TabsTrigger value="REJETEE">Rejetées</TabsTrigger>
          <TabsTrigger value="toutes">Toutes</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Liste */}
      {filtered.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border py-16 text-center text-muted-foreground">
          <CheckCircle2 className="size-8 text-primary" />
          <p className="font-medium">Aucune correction dans cette catégorie.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {filtered.map((c) => {
            const isOpen = expanded === c.id
            const { label, cls } = STATUT_CONFIG[c.statut]
            const initiales = `${c.etudiant.prenom[0]}${c.etudiant.nom[0]}`.toUpperCase()

            return (
              <Card key={c.id} className="rounded-2xl border-border/60 shadow-sm">
                {/* En-tête */}
                <div
                  className="flex cursor-pointer items-center gap-3 p-4"
                  onClick={() => setExpanded(isOpen ? null : c.id)}
                >
                  <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                    {initiales}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-[13px] font-semibold">
                        {c.etudiant.prenom} {c.etudiant.nom}
                      </span>
                      <Badge variant="outline" className={`rounded-full text-[11px] font-semibold ${cls}`}>
                        {label}
                      </Badge>
                    </div>
                    <p className="mt-0.5 truncate text-[11px] text-muted-foreground">
                      {c.evaluation} · {c.question.slice(0, 60)}…
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <div className="text-right">
                      <div className="flex items-center gap-1 text-sm font-bold">
                        <Bot className="size-3.5 text-primary" />
                        {c.noteProposeeIA} / {c.pointsMax}
                      </div>
                      <div className="text-[11px] text-muted-foreground">note IA</div>
                    </div>
                    {isOpen ? <ChevronUp className="size-4 text-muted-foreground" /> : <ChevronDown className="size-4 text-muted-foreground" />}
                  </div>
                </div>

                {/* Détail déplié */}
                {isOpen && (
                  <CardContent className="flex flex-col gap-4 border-t border-border pt-4">

                    {/* Question */}
                    <div>
                      <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">Question</p>
                      <p className="text-sm">{c.question}</p>
                    </div>

                    {/* Réponse étudiant */}
                    <div>
                      <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        <User className="size-3.5" /> Réponse de l'étudiant
                      </p>
                      <div className="rounded-xl border border-border bg-muted/30 px-4 py-3 text-sm">
                        {c.reponse}
                      </div>
                    </div>

                    {/* Justification IA */}
                    <div>
                      <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        <Bot className="size-3.5" /> Analyse de l'IA
                      </p>
                      <div className="rounded-xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm text-muted-foreground">
                        {c.justificationIA}
                      </div>
                    </div>

                    {/* Note finale + actions */}
                    {c.statut === "EN_ATTENTE" && (
                      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-background p-3">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium">Note finale :</span>
                          <Input
                            type="number"
                            min={0}
                            max={c.pointsMax}
                            step={0.5}
                            placeholder={String(c.noteProposeeIA)}
                            value={notes[c.id] ?? ""}
                            onChange={(e) => setNotes((n) => ({ ...n, [c.id]: e.target.value }))}
                            className="w-24 rounded-lg"
                          />
                          <span className="text-sm text-muted-foreground">/ {c.pointsMax}</span>
                        </div>
                        <div className="ml-auto flex gap-2">
                          <Button
                            variant="outline"
                            className="rounded-xl border-destructive/30 text-destructive hover:bg-destructive/10 hover:text-destructive"
                            onClick={() => rejeter(c.id)}
                          >
                            <XCircle className="size-4" /> Rejeter
                          </Button>
                          <Button className="rounded-xl" onClick={() => valider(c.id)}>
                            <CheckCircle2 className="size-4" /> Valider
                          </Button>
                        </div>
                      </div>
                    )}

                    {/* Déjà traitée */}
                    {c.statut !== "EN_ATTENTE" && (
                      <div className="flex items-center gap-2 rounded-xl bg-muted/40 px-4 py-2.5 text-sm text-muted-foreground">
                        <CheckCircle2 className="size-4 text-primary" />
                        Note attribuée : <span className="font-bold text-foreground">{c.noteProposeeIA} / {c.pointsMax}</span>
                      </div>
                    )}

                  </CardContent>
                )}
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
