import { useState } from "react";
import { CheckCircle2 } from "lucide-react";
import type { ReactNode } from "react";
import { PageTitle, Card, Badge } from "@/components/admin/ui";

/* Données mockées — remplacer par GET/PUT /api/admin/settings (react-query). */

export default function ParametresSysteme() {
  return (
    <>
      <PageTitle
        title="Paramètres système"
        subtitle="Configuration générale de la plateforme d'évaluation."
      />

      <div className="mt-6 grid grid-cols-2 gap-4">
        <Card>
          <div className="mb-2 text-base font-bold">Passation &amp; sécurité</div>
          <SettingRow title="Sauvegarde automatique" desc="Intervalle de sauvegarde des réponses pendant la passation." defaultOn value="30 secondes" />
          <SettingRow title="Détection anti-triche" desc="Journaliser les changements d'onglet et pertes de focus." defaultOn />
          <SettingRow title="Marge de tolérance timer" desc="Délai de grâce après expiration avant verrouillage." defaultOn value="10 secondes" />
          <SettingRow title="Soumission auto à expiration" desc="Soumettre automatiquement à la fin du temps imparti." defaultOn />
        </Card>

        <Card>
          <div className="mb-2 text-base font-bold">Correction &amp; IA</div>
          <SettingRow title="Correction IA (questions ouvertes)" desc="Activer l'assistance OpenAI pour les questions ouvertes." defaultOn />
          <SettingRow title="Validation manuelle obligatoire" desc="Toute note IA doit être validée par un enseignant." defaultOn />
          <SettingRow title="Barème négatif QCM" desc="Autoriser les points négatifs sur mauvaise réponse." />
          <SettingRow title="Judge0 — exécution de code" desc="Service d'exécution sécurisée pour les exercices." defaultOn />
        </Card>
      </div>

      <Card className="mt-6">
        <div className="mb-3.5 text-base font-bold">Paramètres généraux</div>
        <div className="grid grid-cols-2 gap-5">
          <Field label="Nom de l'établissement" defaultValue="École Polytechnique de Thiès" />
          <Field
            label="Domaine email autorisé"
            defaultValue="@ept.sn"
            hint="Seuls les emails de ce domaine peuvent s'inscrire."
          />
          <Field label="Rétention des données (mois)" defaultValue="24" type="number" />
          <div className="mb-[18px]">
            <label className="mb-[7px] block text-xs font-semibold">Mode d'inscription</label>
            <select className="w-full rounded-[9px] border border-[#dbe4e4] bg-white px-3 py-2.5 text-[13px] text-[#1a2b2b] outline-none focus:border-[#0d7377]">
              <option>Création par l'administrateur uniquement</option>
              <option>Auto-inscription autorisée</option>
            </select>
          </div>
        </div>
        <div className="mt-2 flex gap-2.5">
          <button className="inline-flex items-center gap-2 rounded-[9px] bg-[#0d7377] px-4 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#0a5a5d]">
            <CheckCircle2 className="h-4 w-4" /> Enregistrer les modifications
          </button>
          <button className="rounded-[9px] border border-[#dbe4e4] bg-white px-4 py-2.5 text-xs font-semibold transition-colors hover:bg-[#f0f7f7]">
            Annuler
          </button>
        </div>
      </Card>
    </>
  );
}

function SettingRow({
  title,
  desc,
  defaultOn = false,
  value,
}: {
  title: string;
  desc: string;
  defaultOn?: boolean;
  value?: string;
}) {
  const [on, setOn] = useState(defaultOn);
  return (
    <div className="flex items-center justify-between border-b border-[#e8eded] py-4 last:border-none">
      <div>
        <h4 className="text-[13px] font-semibold">{title}</h4>
        <p className="mt-0.5 max-w-[440px] text-[11px] text-[#6b7c7c]">{desc}</p>
      </div>
      <div className="flex items-center gap-3">
        {value && <Badge tone="gray">{value}</Badge>}
        <button
          onClick={() => setOn((v) => !v)}
          className={[
            "relative h-[22px] w-10 flex-shrink-0 rounded-full transition-colors",
            on ? "bg-[#0d7377]" : "bg-[#dbe4e4]",
          ].join(" ")}
        >
          <span
            className={[
              "absolute top-0.5 h-[18px] w-[18px] rounded-full bg-white transition-all",
              on ? "left-5" : "left-0.5",
            ].join(" ")}
          />
        </button>
      </div>
    </div>
  );
}

function Field({
  label,
  defaultValue,
  hint,
  type = "text",
}: {
  label: string;
  defaultValue: string;
  hint?: ReactNode;
  type?: string;
}) {
  return (
    <div className="mb-[18px]">
      <label className="mb-[7px] block text-xs font-semibold">{label}</label>
      <input
        type={type}
        defaultValue={defaultValue}
        className="w-full rounded-[9px] border border-[#dbe4e4] bg-white px-3 py-2.5 text-[13px] text-[#1a2b2b] outline-none focus:border-[#0d7377]"
      />
      {hint && <div className="mt-[5px] text-[11px] text-[#94a3a3]">{hint}</div>}
    </div>
  );
}
