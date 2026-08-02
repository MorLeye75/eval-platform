import { Link, useLocation } from "react-router-dom"
import {
  LayoutDashboard,
  ClipboardList,
  Eye,
  CheckCircle2,
  BarChart3,
  Users,
  FileOutput,
  GraduationCap,
  LogOut,
} from "lucide-react"
import { useAuth } from "@/context/AuthContext"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"

const menuItems = [
  { title: "Tableau de bord",     url: "/enseignant",                  icon: LayoutDashboard, end: true },
  { title: "Mes évaluations",     url: "/enseignant/evaluations",      icon: ClipboardList          },
  { title: "Supervision live",    url: "/enseignant/supervision",      icon: Eye                    },
  { title: "Validation IA",       url: "/enseignant/validation-ia",    icon: CheckCircle2           },
  { title: "Statistiques",        url: "/enseignant/statistiques",     icon: BarChart3              },
  { title: "Publication résultats", url: "/enseignant/publication",    icon: Users                  },
  { title: "Export résultats",    url: "/enseignant/export",           icon: FileOutput             },
]

export function EnseignantSidebar() {
  const location = useLocation()
  const { user, logout } = useAuth()

  const initiales = user
    ? `${user.prenom?.[0] ?? ""}${user.nom?.[0] ?? ""}`.toUpperCase()
    : "EN"

  return (
    <Sidebar className="border-r border-border/60">
      <SidebarHeader className="p-3">
        <div className="flex items-center gap-3 rounded-xl px-2 py-2">
          <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary/80 text-primary-foreground shadow-sm">
            <GraduationCap className="size-5" />
          </div>
          <div className="flex flex-col leading-tight">
            <span className="text-sm font-bold tracking-tight">Plateforme</span>
            <span className="text-xs text-muted-foreground">Espace enseignant</span>
          </div>
        </div>
      </SidebarHeader>

      <SidebarContent className="px-2">
        <SidebarGroup>
          <SidebarGroupLabel className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
            Navigation
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-1">
              {menuItems.map((item) => {
                const isActive = item.end
                  ? location.pathname === item.url
                  : location.pathname.startsWith(item.url)
                return (
                  <SidebarMenuItem key={item.url}>
                    <SidebarMenuButton
                      isActive={isActive}
                      className="group h-10 rounded-xl font-medium transition-colors data-[active=true]:bg-primary/10 data-[active=true]:text-primary hover:bg-muted"
                      render={
                        <Link to={item.url}>
                          <item.icon className="size-4 transition-colors group-data-[active=true]:text-primary" />
                          <span>{item.title}</span>
                        </Link>
                      }
                    />
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className="p-3">
        <div className="rounded-xl border border-border/60 bg-muted/40 p-1.5">
          <SidebarMenu className="gap-1">
            <SidebarMenuItem>
              <div className="flex items-center gap-3 rounded-lg px-2 py-1.5 text-sm">
                <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                  {initiales}
                </div>
                <div className="flex min-w-0 flex-col leading-tight">
                  <span className="truncate font-semibold">
                    {user ? `${user.prenom} ${user.nom}` : "Enseignant"}
                  </span>
                  <span className="truncate text-xs text-muted-foreground">
                    {user?.email}
                  </span>
                </div>
              </div>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton
                onClick={logout}
                className="h-9 rounded-lg font-medium text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
              >
                <LogOut className="size-4" />
                <span>Déconnexion</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </div>
      </SidebarFooter>
    </Sidebar>
  )
}
