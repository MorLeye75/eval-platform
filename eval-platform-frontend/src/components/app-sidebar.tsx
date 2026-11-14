import { Link, useLocation } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import {
  LayoutDashboard,
  ClipboardList,
  BarChart3,
  Bell,
  GraduationCap,
  LogOut,
} from "lucide-react"
import { useAuth } from "@/context/AuthContext"
import { CLE_NON_LUES, INTERVALLE_COMPTEUR_MS, nombreNonLues } from "@/lib/notifications"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"

const menuItems = [
  { title: "Tableau de bord", url: "/etudiant/dashboard", icon: LayoutDashboard },
  { title: "Mes évaluations", url: "/etudiant/evaluations", icon: ClipboardList },
  { title: "Mes performances", url: "/etudiant/performances", icon: BarChart3 },
  { title: "Notifications", url: "/etudiant/notifications", icon: Bell },
]

export function AppSidebar() {
  const location = useLocation()
  const { user, logout } = useAuth()
  // Compteur de la cloche (DS-15) : relu toutes les 60 s tant que la page est visible, pas de WebSocket
  const { data: nonLues = 0 } = useQuery({
    queryKey: CLE_NON_LUES,
    queryFn: nombreNonLues,
    refetchInterval: INTERVALLE_COMPTEUR_MS,
    refetchIntervalInBackground: false,
  })

  const initiales = user
    ? `${user.prenom?.[0] ?? ""}${user.nom?.[0] ?? ""}`.toUpperCase()
    : "ÉT"

  return (
    <Sidebar className="border-r border-border/60">
      <SidebarHeader className="p-3">
        <div className="flex items-center gap-3 rounded-xl px-2 py-2">
          <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary/80 text-primary-foreground shadow-sm">
            <GraduationCap className="size-5" />
          </div>
          <div className="flex flex-col leading-tight">
            <span className="text-sm font-bold tracking-tight">Plateforme</span>
            <span className="text-xs text-muted-foreground">Espace étudiant</span>
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
                const isActive = location.pathname.startsWith(item.url)
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
                    {item.url === "/etudiant/notifications" && nonLues > 0 && (
                      <SidebarMenuBadge
                        aria-label={`${nonLues} notification${nonLues > 1 ? "s" : ""} non lue${nonLues > 1 ? "s" : ""}`}
                        className="rounded-full bg-primary text-primary-foreground"
                      >
                        {nonLues > 99 ? "99+" : nonLues}
                      </SidebarMenuBadge>
                    )}
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
                    {user ? `${user.prenom} ${user.nom}` : "Étudiant"}
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