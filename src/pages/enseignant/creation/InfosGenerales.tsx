import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ChevronRight, FileText } from "lucide-react"
import type { StatutEvaluation } from "@/types"

// ============================================================
// Étape 1 / 3 — Informations générales de l'évaluation
// Les données sont passées via state à l'étape suivante
// (BuilderQuestions) et envoyées en une seule fois au backend.
// TODO : POST /api/enseignant/evaluations (depuis BuilderQuestions)
// ============================================================

export interface InfosGeneralesForm {
  titre: string
  description: string
  duree: number          // minutes
  dateOuverture: string  // ISO 8601
  dateFermeture: string  // ISO 8601
  statut: StatutEvaluation
}

const DEFAULT: InfosGeneralesForm = {
  titre: "",
  description: "",
  duree: 60,
  dateOuverture: "",
  dateFermeture: "",
  statut: "BROUILLON",
}

// ──────────────────────────────────────────────────────────
//  Stepper partagé (réutilisé dans les 2 étapes)
// ──────────────────────────────────────────────────────────

export function CreationStepper({ step }: { step: 1 | 2 | 3 }) {
  const steps = [
    { n: 1, label: "Informations générales" },
    { n: 2, label: "Questions"              },
    { n: 3, label: "Prévisualisation"       },
  ]
  return (
    <div className="flex items-center gap-0">
      {steps.map((s, i) => (
        <div key={s.n} className="flex items-center">
          <div className="flex items-center gap-2">
            <div
              className={[
                "flex size-7 items-center justify-center rounded-full text-xs font-bold",
                step >= s.n
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground",
              ].join(" ")}
            >
              {s.n}
            </div>
            <span
              className={[
                "text-sm font-medium",
                step === s.n ? "text-foreground" : "text-muted-foreground",
              ].join(" ")}
            >
              {s.label}
            </span>
          </div>
          {i < steps.length - 1 && (
            <ChevronRight className="mx-3 size-4 text-muted-foreground" />
          )}
        </div>
      ))}
    </div>
  )
}

// ──────────────────────────────────────────────────────────
//  Composant principal
// ──────────────────────────────────────────────────────────

export default function InfosGenerales() {
  const navigate = useNavigate()
  const [form, setForm] = useState<InfosGeneralesForm>(DEFAULT)
  const [errors, setErrors] = useState<Partial<Record<keyof InfosGeneralesForm, string>>>({})

  function set<K extends keyof InfosGeneralesForm>(key: K, val: InfosGeneralesForm[K]) {
    setForm((f) => ({ ...f, [key]: val }))
    setErrors((e) => ({ ...e, [key]: undefined }))
  }

  function validate(): boolean {
    const newErrors: typeof errors = {}
    if (!form.titre.trim())       newErrors.titre        = "Le titre est obligatoire."
    if (!form.description.trim()) newErrors.description  = "La description est obligatoire."
    if (form.duree < 5)           newErrors.duree        = "La durée doit être d'au moins 5 minutes."
    if (!form.dateOuverture)      newErrors.dateOuverture = "La date d'ouverture est obligatoire."
    if (!form.dateFermeture)      newErrors.dateFermeture = "La date de fermeture est obligatoire."
    if (form.dateOuverture && form.dateFermeture && form.dateFermeture <= form.dateOuverture)
      newErrors.dateFermeture = "La fermeture doit être après l'ouverture."
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  function handleNext() {
    if (!validate()) return
    // On passe les données à l'étape 2 via le state de navigation
    navigate("/enseignant/evaluations/creation/questions", { state: { infos: form } })
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">

      {/* En-tête + stepper */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Nouvelle évaluation</h1>
        <p className="mt-1 text-muted-foreground">Renseignez les informations générales.</p>
      </div>
      <CreationStepper step={1} />

      {/* Formulaire */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base font-bold">
            <FileText className="size-5 text-primary" />
            Informations générales
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-5">

          {/* Titre */}
          <div className="flex flex-col gap-1.5">
            <Label>Titre <span className="text-destructive">*</span></Label>
            <Input
              value={form.titre}
              onChange={(e) => set("titre", e.target.value)}
              placeholder="Ex : Algorithmique — exercice de code"
              className="rounded-xl"
            />
            {errors.titre && <p className="text-xs text-destructive">{errors.titre}</p>}
          </div>

          {/* Description */}
          <div className="flex flex-col gap-1.5">
            <Label>Description <span className="text-destructive">*</span></Label>
            <textarea
              value={form.description}
              onChange={(e) => set("description", e.target.value)}
              placeholder="Consignes générales, objectifs de l'évaluation..."
              rows={4}
              className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary resize-none"
            />
            {errors.description && <p className="text-xs text-destructive">{errors.description}</p>}
          </div>

          {/* Durée + Statut */}
          <div className="grid gap-4 md:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label>Durée (minutes) <span className="text-destructive">*</span></Label>
              <Input
                type="number"
                min={5}
                value={form.duree}
                onChange={(e) => set("duree", Number(e.target.value))}
                className="rounded-xl"
              />
              {errors.duree && <p className="text-xs text-destructive">{errors.duree}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Statut initial</Label>
              <Select
                value={form.statut}
                onValueChange={(v) => set("statut", v as StatutEvaluation)}
              >
                <SelectTrigger className="rounded-xl">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="BROUILLON">Brouillon (non visible)</SelectItem>
                  <SelectItem value="PUBLIEE">Publier immédiatement</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Dates */}
          <div className="grid gap-4 md:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label>Date d'ouverture <span className="text-destructive">*</span></Label>
              <Input
                type="datetime-local"
                value={form.dateOuverture}
                onChange={(e) => set("dateOuverture", e.target.value)}
                className="rounded-xl"
              />
              {errors.dateOuverture && <p className="text-xs text-destructive">{errors.dateOuverture}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Date de fermeture <span className="text-destructive">*</span></Label>
              <Input
                type="datetime-local"
                value={form.dateFermeture}
                onChange={(e) => set("dateFermeture", e.target.value)}
                className="rounded-xl"
              />
              {errors.dateFermeture && <p className="text-xs text-destructive">{errors.dateFermeture}</p>}
            </div>
          </div>

        </CardContent>
      </Card>

      {/* Navigation */}
      <div className="flex justify-end gap-3">
        <Button variant="outline" className="rounded-xl" onClick={() => navigate(-1)}>
          Annuler
        </Button>
        <Button className="rounded-xl" onClick={handleNext}>
          Suivant — Questions <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  )
}
