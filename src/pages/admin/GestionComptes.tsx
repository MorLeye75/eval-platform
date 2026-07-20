import { useState } from "react";
import { Search, Plus, Pencil, Trash2 } from "lucide-react";
import { PageTitle, Card, Badge, Avatar, IconButton } from "@/components/admin/ui";

/* Données mockées — remplacer par GET /api/admin/users (react-query). */
type Role = "Étudiant" | "Enseignant" | "Admin";
type Statut = "Actif" | "Désactivé" | "Suspendu";

interface User {
  name: string;
  email: string;
  role: Role;
  statut: Statut;
  lastSeen: string;
}

const USERS: User[] = [
  { name: "Fatou Sarr", email: "fatou.sarr@ept.sn", role: "Enseignant", statut: "Actif", lastSeen: "il y a 5 min" },
  { name: "Moussa Diop", email: "moussa.diop@ept.sn", role: "Étudiant", statut: "Actif", lastSeen: "1 h" },
  { name: "Awa Ndiaye", email: "awa.ndiaye@ept.sn", role: "Étudiant", statut: "Actif", lastSeen: "3 h" },
  { name: "Ibrahima Fall", email: "i.fall@ept.sn", role: "Enseignant", statut: "Désactivé", lastSeen: "12 j" },
  { name: "Sokhna Mbaye", email: "sokhna.mbaye@ept.sn", role: "Admin", statut: "Actif", lastSeen: "2 h" },
  { name: "Cheikh Ba", email: "cheikh.ba@ept.sn", role: "Étudiant", statut: "Suspendu", lastSeen: "1 j" },
  { name: "Mariama Sy", email: "mariama.sy@ept.sn", role: "Étudiant", statut: "Actif", lastSeen: "30 min" },
];

const roleTone = { Étudiant: "purple", Enseignant: "blue", Admin: "green" } as const;
const statutTone = { Actif: "green", Désactivé: "gray", Suspendu: "amber" } as const;

const TABS = ["Tous", "Étudiants", "Enseignants", "Admins"] as const;

export default function GestionComptes() {
  const [tab, setTab] = useState<(typeof TABS)[number]>("Tous");
  const [query, setQuery] = useState("");

  const filtered = USERS.filter((u) => {
    const matchTab =
      tab === "Tous" ||
      (tab === "Étudiants" && u.role === "Étudiant") ||
      (tab === "Enseignants" && u.role === "Enseignant") ||
      (tab === "Admins" && u.role === "Admin");
    const matchQuery =
      u.name.toLowerCase().includes(query.toLowerCase()) ||
      u.email.toLowerCase().includes(query.toLowerCase());
    return matchTab && matchQuery;
  });

  return (
    <>
      <div className="flex items-start justify-between">
        <PageTitle
          title="Gestion des comptes"
          subtitle="Créez, modifiez ou désactivez les comptes utilisateurs."
        />
        <button className="inline-flex items-center gap-2 rounded-[9px] bg-[#0d7377] px-4 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#0a5a5d]">
          <Plus className="h-4 w-4" /> Nouveau compte
        </button>
      </div>

      <div className="mt-5 flex flex-wrap items-center justify-between gap-3.5">
        <div className="flex max-w-[360px] flex-1 items-center gap-2 rounded-[9px] border border-[#dbe4e4] bg-white px-3 py-2 text-[#6b7c7c]">
          <Search className="h-4 w-4" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher par nom ou email..."
            className="flex-1 bg-transparent text-[13px] text-[#1a2b2b] outline-none"
          />
        </div>
        <div className="flex gap-1 rounded-[9px] bg-[#eef2f2] p-1">
          {TABS.map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={[
                "rounded-[7px] px-3 py-1.5 text-xs transition-colors",
                tab === t ? "bg-white font-semibold text-[#0a5a5d]" : "text-[#6b7c7c]",
              ].join(" ")}
            >
              {t}
            </button>
          ))}
        </div>
      </div>

      <Card className="mt-4 p-0">
        <table className="w-full border-collapse">
          <thead>
            <tr>
              {["Utilisateur", "Rôle", "Statut", "Dernière connexion"].map((h) => (
                <th
                  key={h}
                  className="border-b border-[#e8eded] px-3 py-2.5 text-left text-[11px] font-semibold uppercase tracking-[0.5px] text-[#94a3a3]"
                >
                  {h}
                </th>
              ))}
              <th className="border-b border-[#e8eded] px-3 py-2.5 text-right text-[11px] font-semibold uppercase tracking-[0.5px] text-[#94a3a3]">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((u) => (
              <tr key={u.email} className="group">
                <td className="border-b border-[#e8eded] px-3 py-3 group-last:border-none">
                  <div className="flex items-center gap-2.5">
                    <Avatar name={u.name} />
                    <div>
                      <div className="text-[13px] font-semibold">{u.name}</div>
                      <div className="text-[11px] text-[#6b7c7c]">{u.email}</div>
                    </div>
                  </div>
                </td>
                <td className="border-b border-[#e8eded] px-3 py-3 group-last:border-none">
                  <Badge tone={roleTone[u.role]}>{u.role}</Badge>
                </td>
                <td className="border-b border-[#e8eded] px-3 py-3 group-last:border-none">
                  <Badge tone={statutTone[u.statut]} dot>{u.statut}</Badge>
                </td>
                <td className="border-b border-[#e8eded] px-3 py-3 text-[13px] text-[#6b7c7c] group-last:border-none">
                  {u.lastSeen}
                </td>
                <td className="border-b border-[#e8eded] px-3 py-3 text-right group-last:border-none">
                  <div className="flex justify-end gap-2">
                    <IconButton icon={Pencil} />
                    <IconButton icon={Trash2} />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <div className="mt-4 flex items-center justify-between text-xs text-[#6b7c7c]">
        <span>
          {filtered.length} sur {USERS.length} utilisateurs
        </span>
        <div className="flex gap-1.5">
          <button className="rounded-[9px] border border-[#dbe4e4] bg-white px-2.5 py-1.5 font-semibold transition-colors hover:bg-[#f0f7f7]">
            ← Précédent
          </button>
          <button className="rounded-[9px] border border-[#dbe4e4] bg-white px-2.5 py-1.5 font-semibold transition-colors hover:bg-[#f0f7f7]">
            Suivant →
          </button>
        </div>
      </div>
    </>
  );
}
