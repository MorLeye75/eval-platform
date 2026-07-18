import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { ProtectedRoute } from "./ProtectedRoute"
import LoginPage from "@/pages/LoginPage"
import { EtudiantLayout } from "@/layouts/EtudiantLayout"
import DashboardEtudiant from "@/pages/etudiant/DashboardEtudiant"
import ListeEvaluations from "@/pages/etudiant/ListeEvaluations"
import DetailEvaluation from "@/pages/etudiant/DetailEvaluation"

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Routes publiques */}
        <Route path="/login" element={<LoginPage />} />

        {/* Espace étudiant */}
        <Route
          path="/etudiant"
          element={
            <ProtectedRoute allowedRoles={["ETUDIANT"]}>
              <EtudiantLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/etudiant/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardEtudiant />} />
          <Route path="evaluations" element={<ListeEvaluations />} />
          <Route path="evaluations/:id" element={<DetailEvaluation />} />
          {/* Futures pages étudiant :
              <Route path="resultats" element={<ResultatsDetail />} />
              <Route path="performances" element={<TableauBordPerformance />} />
              <Route path="notifications" element={<NotificationsPage />} /> */}
        </Route>

        {/* Espace enseignant */}
        <Route
          path="/enseignant/*"
          element={
            <ProtectedRoute allowedRoles={["ENSEIGNANT"]}>
              <div>Espace enseignant — pages à venir</div>
            </ProtectedRoute>
          }
        />

        {/* Espace admin */}
        <Route
          path="/admin/*"
          element={
            <ProtectedRoute allowedRoles={["ADMIN"]}>
              <div>Espace admin — pages à venir</div>
            </ProtectedRoute>
          }
        />

        {/* Par défaut → login */}
        <Route path="/" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}