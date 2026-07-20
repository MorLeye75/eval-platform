import {
  Users,
  FileText,
  CheckCircle2,
  AlertTriangle,
  Database,
  Shield,
  Award,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Doughnut } from "react-chartjs-2";
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
} from "chart.js";
import { PageTitle, Card, StatCard, CardHead, Badge } from "@/components/admin/ui";

ChartJS.register(ArcElement, Tooltip, Legend);

/* NOTE: données mockées — à remplacer par un appel react-query vers
   GET /api/admin/dashboard une fois le backend prêt. */

const roleData = {
  labels: ["Étudiants", "Enseignants", "Admins"],
  datasets: [
    {
      data: [298, 38, 6],
      backgroundColor: ["#0d7377", "#5aa5a8", "#b8d8d9"],
      borderWidth: 0,
    },
  ],
};

export default function DashboardAdmin() {
  return (
    <>
      <PageTitle
        title="Tableau de bord"
        subtitle="Vue d'ensemble de l'activité de la plateforme."
      />

      <div className="mt-6 grid grid-cols-4 gap-4">
        <StatCard label="Utilisateurs" value="342" icon={Users} hint="+18 ce mois" dir="up" />
        <StatCard label="Évaluations actives" value="7" icon={FileText} hint="3 en cours" />
        <StatCard label="Soumissions (7j)" value="1 284" icon={CheckCircle2} hint="+9% vs semaine" dir="up" />
        <StatCard label="Incidents signalés" value="2" icon={AlertTriangle} hint="à examiner" dir="down" />
      </div>

      <div className="mt-6 grid grid-cols-[1.6fr_1fr] gap-4">
        <Card>
          <CardHead
            title="Activité récente"
            action={<span className="cursor-pointer text-xs text-[#0d7377]">Tout voir →</span>}
          />
          <ActivityRow icon={Users} title="Nouveau compte enseignant" sub="Dr. Fatou Sarr · il y a 12 min" tone="green" tag="Créé" />
          <ActivityRow icon={FileText} title="Évaluation publiée" sub="Algorithmique — exercice de code · 1 h" tone="blue" tag="Publiée" />
          <ActivityRow icon={AlertTriangle} title="Changements d'onglet détectés" sub="Étudiant #217 · Réseaux QCM · 2 h" tone="amber" tag="Alerte" />
          <ActivityRow icon={CheckCircle2} title="Résultats publiés" sub="Bases de données — quiz · 3 h" tone="green" tag="Publiés" />
          <ActivityRow icon={Users} title="Compte désactivé" sub="Étudiant inactif · 5 h" tone="gray" tag="Désactivé" />
        </Card>

        <Card>
          <CardHead title="Répartition des rôles" />
          <div className="mx-auto mt-2 h-[180px]">
            <Doughnut data={roleData} options={{ cutout: "65%", plugins: { legend: { display: false } } }} />
          </div>
          <div className="mt-3.5">
            <KV k="Étudiants" v="298" />
            <KV k="Enseignants" v="38" />
            <KV k="Administrateurs" v="6" />
          </div>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHead title="État des services" />
        <div className="mt-3 grid grid-cols-3 gap-4">
          <ServiceCard icon={Database} name="Base de données" status="Opérationnel" tone="green" />
          <ServiceCard icon={Shield} name="Judge0 (exécution code)" status="Opérationnel" tone="green" />
          <ServiceCard icon={Award} name="API OpenAI" status="Latence élevée" tone="amber" />
        </div>
      </Card>
    </>
  );
}

function ActivityRow({
  icon: Icon,
  title,
  sub,
  tone,
  tag,
}: {
  icon: LucideIcon;
  title: string;
  sub: string;
  tone: "green" | "amber" | "red" | "blue" | "purple" | "gray";
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

function KV({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between border-b border-[#e8eded] py-2.5 text-[13px] last:border-none">
      <span className="text-[#6b7c7c]">{k}</span>
      <span className="font-semibold">{v}</span>
    </div>
  );
}

function ServiceCard({
  icon: Icon,
  name,
  status,
  tone,
}: {
  icon: LucideIcon;
  name: string;
  status: string;
  tone: "green" | "amber" | "red";
}) {
  return (
    <div className="flex items-center gap-3 rounded-[11px] border border-[#e8eded] p-3.5">
      <div className="flex h-[38px] w-[38px] items-center justify-center rounded-[10px] bg-[#f0f7f7] text-[#0d7377]">
        <Icon className="h-[18px] w-[18px]" />
      </div>
      <div className="flex-1">
        <div className="text-[13px] font-semibold">{name}</div>
        <div className="mt-1">
          <Badge tone={tone} dot>{status}</Badge>
        </div>
      </div>
    </div>
  );
}
