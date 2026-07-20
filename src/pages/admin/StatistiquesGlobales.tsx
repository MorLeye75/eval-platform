import { Award, CheckCircle2, FileText, BarChart3 } from "lucide-react";
import { Bar, Doughnut } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  ArcElement,
  Tooltip,
  Legend,
} from "chart.js";
import { PageTitle, Card, StatCard, CardHead, Badge } from "@/components/admin/ui";

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend);

/* Données mockées — remplacer par GET /api/admin/stats (react-query). */

const monoTicks = { font: { family: "'JetBrains Mono'", size: 11 }, color: "#6b7c7c" };

const subData = {
  labels: ["Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil"],
  datasets: [
    {
      data: [145, 198, 176, 234, 289, 312, 284],
      backgroundColor: "#0d7377",
      borderRadius: 6,
    },
  ],
};

const typeData = {
  labels: ["QCM", "Code", "Ouvertes", "Quiz"],
  datasets: [
    {
      data: [42, 23, 20, 15],
      backgroundColor: ["#0d7377", "#5aa5a8", "#8fc1c3", "#c5e0e1"],
      borderWidth: 0,
    },
  ],
};

const DEPTS = [
  ["Informatique", "64", "142", "14,1 / 20", "82 %"],
  ["Télécommunications", "41", "98", "13,2 / 20", "76 %"],
  ["Génie Civil", "28", "76", "12,8 / 20", "71 %"],
  ["Génie Électrique", "23", "64", "13,5 / 20", "79 %"],
];

export default function StatistiquesGlobales() {
  return (
    <>
      <PageTitle
        title="Statistiques globales"
        subtitle="Indicateurs de performance de l'ensemble de la plateforme."
      />

      <div className="mt-6 grid grid-cols-4 gap-4">
        <StatCard label="Moyenne plateforme" value="13,4 / 20" icon={Award} hint="+0,3 vs semestre" dir="up" />
        <StatCard label="Taux de réussite" value="78 %" icon={CheckCircle2} hint="≥ 10/20" dir="up" />
        <StatCard label="Évaluations créées" value="156" icon={FileText} hint="cette année" />
        <StatCard label="Taux de complétion" value="91 %" icon={BarChart3} hint="soumissions finalisées" dir="up" />
      </div>

      <div className="mt-6 grid grid-cols-[1.6fr_1fr] gap-4">
        <Card>
          <CardHead title="Soumissions par mois" />
          <div className="mt-3 h-[280px]">
            <Bar
              data={subData}
              options={{
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                  y: { ticks: monoTicks, grid: { color: "#eef2f2" } },
                  x: { ticks: monoTicks, grid: { display: false } },
                },
              }}
            />
          </div>
        </Card>

        <Card>
          <CardHead title="Répartition par type" />
          <div className="mt-5 h-[200px]">
            <Doughnut
              data={typeData}
              options={{
                maintainAspectRatio: false,
                cutout: "60%",
                plugins: {
                  legend: {
                    position: "bottom",
                    labels: {
                      font: { family: "'JetBrains Mono'", size: 11 },
                      color: "#6b7c7c",
                      padding: 14,
                      boxWidth: 12,
                    },
                  },
                },
              }}
            />
          </div>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHead title="Top départements par activité" />
        <table className="mt-2 w-full border-collapse">
          <thead>
            <tr>
              {["Département", "Évaluations", "Étudiants", "Moyenne", "Taux réussite"].map((h) => (
                <th
                  key={h}
                  className="border-b border-[#e8eded] px-3 py-2.5 text-left text-[11px] font-semibold uppercase tracking-[0.5px] text-[#94a3a3]"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {DEPTS.map((d) => (
              <tr key={d[0]}>
                <td className="border-b border-[#e8eded] px-3 py-3 text-[13px] font-semibold last:border-none">{d[0]}</td>
                <td className="border-b border-[#e8eded] px-3 py-3 text-[13px]">{d[1]}</td>
                <td className="border-b border-[#e8eded] px-3 py-3 text-[13px]">{d[2]}</td>
                <td className="border-b border-[#e8eded] px-3 py-3 text-[13px]">{d[3]}</td>
                <td className="border-b border-[#e8eded] px-3 py-3 text-[13px]">
                  <Badge tone="green">{d[4]}</Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  );
}
