import { NavLink, Outlet } from "react-router-dom";
import {
  LayoutDashboard,
  Users,
  Eye,
  BarChart3,
  Settings,
  LogOut,
  Menu,
} from "lucide-react";

const NAV = [
  { to: "/admin", end: true, icon: LayoutDashboard, label: "Tableau de bord" },
  { to: "/admin/comptes", icon: Users, label: "Gestion des comptes" },
  { to: "/admin/supervision", icon: Eye, label: "Supervision en cours" },
  { to: "/admin/statistiques", icon: BarChart3, label: "Statistiques globales" },
  { to: "/admin/parametres", icon: Settings, label: "Paramètres système" },
];

/**
 * Layout de l'espace Administrateur.
 * Reprend la charte commune : police mono, teal (#0d7377), sidebar blanche.
 * À brancher dans AppRouter.tsx sous une route protégée role=ADMIN.
 */
export default function AdminLayout() {
  return (
    <div className="flex min-h-screen bg-[#f8fafb] font-mono text-[#1a2b2b]">
      {/* Sidebar */}
      <aside className="sticky top-0 flex h-screen w-[250px] flex-shrink-0 flex-col border-r border-[#e8eded] bg-white">
        <div className="flex items-center gap-3 px-[18px] py-5">
          <div className="flex h-10 w-10 items-center justify-center rounded-[11px] bg-[#0d7377] text-lg text-white">
            ◈
          </div>
          <div>
            <div className="text-[15px] font-bold">Plateforme</div>
            <div className="text-[11px] text-[#6b7c7c]">Espace admin</div>
          </div>
        </div>

        <div className="px-5 pb-2 pt-3.5 text-[10px] font-semibold tracking-[1.5px] text-[#94a3a3]">
          NAVIGATION
        </div>

        <nav className="flex flex-col">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              className={({ isActive }) =>
                [
                  "mx-3 my-0.5 flex items-center gap-3 rounded-[9px] px-[18px] py-2.5 text-[13px] transition-colors",
                  isActive
                    ? "bg-[#e6f2f2] font-semibold text-[#0a5a5d]"
                    : "text-[#1a2b2b] hover:bg-[#f0f7f7]",
                ].join(" ")
              }
            >
              <n.icon className="h-[17px] w-[17px] flex-shrink-0" />
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto border-t border-[#e8eded] p-3.5">
          <div className="flex items-center gap-2.5 rounded-[9px] bg-[#f0f7f7] p-2">
            <div className="flex h-[34px] w-[34px] items-center justify-center rounded-lg bg-[#e6f2f2] text-xs font-bold text-[#0a5a5d]">
              AD
            </div>
            <div>
              <div className="text-[13px] font-semibold">Admin Test</div>
              <div className="text-[11px] text-[#6b7c7c]">admin@ept.sn</div>
            </div>
          </div>
          <button className="mt-1 flex w-full items-center gap-2.5 px-2 py-2.5 text-[13px] text-[#6b7c7c] transition-colors hover:text-[#b91c1c]">
            <LogOut className="h-4 w-4" />
            Déconnexion
          </button>
        </div>
      </aside>

      {/* Main */}
      <main className="min-w-0 flex-1">
        <div className="sticky top-0 z-10 flex items-center gap-3.5 border-b border-[#e8eded] bg-white px-8 py-4">
          <Menu className="h-5 w-5 cursor-pointer text-[#6b7c7c]" />
          <span className="text-sm text-[#6b7c7c]">Espace administrateur</span>
        </div>
        <div className="max-w-[1400px] p-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
