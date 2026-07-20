import { Eye, Users, AlertTriangle, FileText, Clock } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { PageTitle, Card, StatCard, CardHead, Badge } from "@/components/admin/ui";

/* Données mockées — à remplacer par un flux temps réel
   (polling react-query ou WebSocket) vers GET /api/admin/supervision. */

interface LiveEval {
  title: string;
  teacher: string;
  submitted: string;
  progress: number;
}

const EVALS: LiveEval[] = [
  { title: "Structures de données — QCM", teacher: "Dr. Fatou Sarr", submitted: "42 / 45", progress: 93 },
  { title: "Algorithmique — exercice de code", teacher: "Dr. Ibrahima Fall", submitted: "28 / 40", progress: 70 },
  { title: "Réseaux — questions ouvertes", teacher: "Pr. Sokhna Mbaye", submitted: "15 / 38", progress: 39 },
];

export default function SupervisionGlobale() {
  return (
    <>
      <PageTitle
        title="Supervision en temps réel"
        subtitle="Suivi des évaluations actuellement en cours de passation."
      />

      <div className="mt-6 grid grid-cols-3 gap-4">
        <StatCard label="Évaluations en cours" value="3" icon={Eye} hint="live" dir="up" />
        <StatCard label="Étudiants connectés" value="85" icon={Users} hint="sur 123 attendus" />
        <StatCard label="Alertes anti-triche" value="4" icon={AlertTriangle} hint="à surveiller" dir="down" />
      </div>

      <div className="mt-6 mb-1 flex items-center justify-between">
        <div className="text-base font-bold">Évaluations en cours</div>
        <Badge tone="green" dot>Mise à jour live</Badge>
      </div>

      {EVALS.map((e) => (
        <Card key={e.title} className="mt-3">
          <div className="flex items-center gap-3.5">
            <div className="flex h-[38px] w-[38px] items-center justify-center rounded-[10px] bg-[#f0f7f7] text-[#0d7377]">
              <FileText className="h-[18px] w-[18px]" />
            </div>
            <div className="flex-1">
              <div className="text-sm font-semibold">{e.title}</div>
              <div className="mt-0.5 text-[11px] text-[#6b7c7c]">
                {e.teacher} · {e.submitted} soumissions
              </div>
              <div className="mt-2 h-[7px] overflow-hidden rounded-[10px] bg-[#e8eded]">
                <div className="h-full rounded-[10px] bg-[#0d7377]" style={{ width: `${e.progress}%` }} />
              </div>
            </div>
            <div className="text-right">
              <div className="text-xl font-extrabold">{e.progress}%</div>
              <button className="mt-1.5 inline-flex items-center gap-2 rounded-[9px] border border-[#dbe4e4] bg-white px-2.5 py-1.5 text-[11px] font-semibold transition-colors hover:bg-[#f0f7f7]">
                <Eye className="h-3.5 w-3.5" /> Détails
              </button>
            </div>
          </div>
        </Card>
      ))}

      <Card className="mt-6">
        <CardHead title="Alertes anti-triche récentes" />
        <AlertRow icon={AlertTriangle} title="Changement d'onglet répété" sub="Étudiant #217 · Réseaux — 4 fois" tone="amber" tag="Modéré" />
        <AlertRow icon={AlertTriangle} title="Perte de focus prolongée" sub="Étudiant #142 · Algorithmique — 2 min" tone="red" tag="Élevé" />
        <AlertRow icon={Clock} title="Soumission à expiration" sub="Étudiant #089 · QCM — auto-soumis" tone="gray" tag="Info" />
      </Card>
    </>
  );
}

function AlertRow({
  icon: Icon,
  title,
  sub,
  tone,
  tag,
}: {
  icon: LucideIcon;
  title: string;
  sub: string;
  tone: "amber" | "red" | "gray";
  tag: string;
}) {
  return (
    <div className="flex items-center gap-3 border-b border-[#e8eded] py-3 last:border-none">
      <div className="flex h-[38px] w-[38px] flex-shrink-0 items-center justify-center rounded-[10px] bg-[#f0f7f7] text-[#0d7377]">
        <Icon className="h-[18px] w-[18px]" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-semibold">{title}</div>
        <div className="mt-0.5 text-[11px] text-[#6b7c7c]">{sub}</div>
      </div>
      <Badge tone={tone}>{tag}</Badge>
    </div>
  );
}
