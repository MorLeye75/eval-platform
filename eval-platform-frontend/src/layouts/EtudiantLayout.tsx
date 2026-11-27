import { Outlet } from "react-router-dom"
import { AppSidebar } from "@/components/app-sidebar"
import { Separator } from "@/components/ui/separator"
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar"

export function EtudiantLayout() {
  return (
    <SidebarProvider>
      <AppSidebar />
      {/* min-w-0 : sans lui, la zone principale (enfant flex) s'élargit à son contenu le plus large */}
      <SidebarInset className="min-w-0">
        <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-2 h-4" />
          <span className="text-sm font-medium">Espace étudiant</span>
        </header>
        {/* En bloc et non en flex : une page centrée par mx-auto prend la largeur de l'écran, et un tableau
            large défile dans son cadre au lieu d'élargir toute la page (affichage sur téléphone) */}
        <div className="min-w-0 flex-1 p-4 md:p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}