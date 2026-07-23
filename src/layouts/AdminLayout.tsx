import { Outlet } from "react-router-dom"
import { AdminSidebar } from "@/components/AdminSidebar"
import { Separator } from "@/components/ui/separator"
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar"

/**
 * Layout de l'espace Administrateur.
 * Suit exactement le même pattern que EtudiantLayout :
 * SidebarProvider > AdminSidebar + SidebarInset > header + Outlet
 *
 * Emplacement : src/layouts/AdminLayout.tsx  (pas pages/admin/)
 */
export function AdminLayout() {
  return (
    <SidebarProvider>
      <AdminSidebar />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-2 h-4" />
          <span className="text-sm font-medium text-muted-foreground">
            Espace administrateur
          </span>
        </header>
        <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
