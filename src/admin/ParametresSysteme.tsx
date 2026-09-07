import { useState } from "react"
import { CheckCircle2 } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"

// ============================================================
// Données mockées — à remplacer par :
// const { data } = useQuery({ queryKey: ["admin-settings"], queryFn: () => api.get("/admin/settings") })
// Sauvegarde : useMutation(() => api.put("/admin/settings", form))
// ============================================================

interface Settings {
  sauvegardeAuto: boolean
  intervalSauvegarde: number
  detectionAntTriche: boolean
  margeToleranceTimer: number
  soumissionAutoExpiration: boolean
  correctionIA: boolean
  validationManuelleObligatoire: boolean
  baremeNegatifQCM: boolean
  judge0Actif: boolean
  nomEtablissement: string
  domaineEmail: string
  retentionDonnees: number
  modeInscription: "ADMIN_UNIQUEMENT" | "AUTO"
}

const DEFAULT: Settings = {
  sauvegardeAuto: true,
  intervalSauvegarde: 30,
  detectionAntTriche: true,
  margeToleranceTimer: 10,
  soumissionAutoExpiration: true,
  correctionIA: true,
  validationManuelleObligatoire: true,
  baremeNegatifQCM: false,
  judge0Actif: true,
  nomEtablissement: "École Polytechnique de Thiès",
  domaineEmail: "@ept.sn",
  retentionDonnees: 24,
  modeInscription: "ADMIN_UNIQUEMENT",
}

export default function ParametresSysteme() {
  const [form, setForm] = useState<Settings>(DEFAULT)

  function toggle(key: keyof Settings) {
    setForm((f) => ({ ...f, [key]: !f[key] }))
  }

  function set<K extends keyof Settings>(key: K, val: Settings[K]) {
    setForm((f) => ({ ...f, [key]: val }))
  }

  function handleSave() {
    // TODO : api.put("/admin/settings", form)
    console.log("Sauvegarde :", form)
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Paramètres système</h1>
        <p className="mt-1 text-muted-foreground">Configuration générale de la plateforme d'évaluation.</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Passation &amp; sécurité</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-0">
            <SettingRow title="Sauvegarde automatique"    desc="Sauvegarde des réponses pendant la passation."              checked={form.sauvegardeAuto}           onToggle={() => toggle("sauvegardeAuto")}           badge={`${form.intervalSauvegarde} s`} />
            <SettingRow title="Détection anti-triche"     desc="Journaliser les changements d'onglet et pertes de focus."   checked={form.detectionAntTriche}        onToggle={() => toggle("detectionAntTriche")}        />
            <SettingRow title="Marge de tolérance timer"  desc="Délai de grâce après expiration avant verrouillage."       checked={true}                           onToggle={() => {}}                                  badge={`${form.margeToleranceTimer} s`} readOnly />
            <SettingRow title="Soumission auto à expiration" desc="Soumettre automatiquement à la fin du temps imparti."   checked={form.soumissionAutoExpiration}  onToggle={() => toggle("soumissionAutoExpiration")}  />
          </CardContent>
        </Card>

        <Card className="rounded-2xl border-border/60 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-bold">Correction &amp; IA</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-0">
            <SettingRow title="Correction IA (questions ouvertes)"  desc="Activer l'assistance OpenAI pour les questions ouvertes." checked={form.correctionIA}                    onToggle={() => toggle("correctionIA")}                    />
            <SettingRow title="Validation manuelle obligatoire"     desc="Toute note IA doit être validée par un enseignant."       checked={form.validationManuelleObligatoire}   onToggle={() => toggle("validationManuelleObligatoire")}   />
            <SettingRow title="Barème négatif QCM"                  desc="Autoriser les points négatifs sur mauvaise réponse."      checked={form.baremeNegatifQCM}               onToggle={() => toggle("baremeNegatifQCM")}                />
            <SettingRow title="Judge0 — exécution de code"          desc="Service d'exécution sécurisée pour les exercices."        checked={form.judge0Actif}                    onToggle={() => toggle("judge0Actif")}                     />
          </CardContent>
        </Card>
      </div>

      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-bold">Paramètres généraux</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-5 md:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label>Nom de l'établissement</Label>
              <Input value={form.nomEtablissement} onChange={(e) => set("nomEtablissement", e.target.value)} className="rounded-xl" />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Domaine email autorisé</Label>
              <Input value={form.domaineEmail} onChange={(e) => set("domaineEmail", e.target.value)} className="rounded-xl" />
              <p className="text-[11px] text-muted-foreground">Seuls les emails de ce domaine peuvent s'inscrire.</p>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Rétention des données (mois)</Label>
              <Input type="number" value={form.retentionDonnees} onChange={(e) => set("retentionDonnees", Number(e.target.value))} className="rounded-xl" />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Mode d'inscription</Label>
              <Select value={form.modeInscription} onValueChange={(v) => set("modeInscription", v as Settings["modeInscription"])}>
                <SelectTrigger className="rounded-xl"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ADMIN_UNIQUEMENT">Admin uniquement</SelectItem>
                  <SelectItem value="AUTO">Auto-inscription</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* ✅ Button shadcn au lieu de <button> brut */}
          <div className="mt-6 flex gap-2.5">
            <Button className="rounded-xl" onClick={handleSave}>
              <CheckCircle2 className="size-4" /> Enregistrer les modifications
            </Button>
            <Button variant="outline" className="rounded-xl" onClick={() => setForm(DEFAULT)}>
              Annuler
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function SettingRow({ title, desc, checked, onToggle, badge, readOnly = false }: {
  title: string; desc: string; checked: boolean; onToggle: () => void; badge?: string; readOnly?: boolean
}) {
  return (
    <div className="flex items-center justify-between border-b border-border py-4 last:border-none">
      <div className="pr-4">
        <p className="text-[13px] font-semibold">{title}</p>
        <p className="mt-0.5 max-w-[360px] text-[11px] text-muted-foreground">{desc}</p>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        {badge && <span className="rounded-full bg-muted px-2.5 py-[3px] text-[11px] font-semibold text-muted-foreground">{badge}</span>}
        <Switch checked={checked} onCheckedChange={readOnly ? undefined : onToggle} disabled={readOnly} />
      </div>
    </div>
  )
}
