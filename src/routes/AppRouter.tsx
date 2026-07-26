import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { ProtectedRoute } from "./ProtectedRoute"
import LoginPage from "@/pages/LoginPage"
import { EtudiantLayout } from "@/layouts/EtudiantLayout"
import { ExamenLayout } from "@/layouts/ExamenLayout"
import DashboardEtudiant from "@/pages/etudiant/DashboardEtudiant"
import ListeEvaluations from "@/pages/etudiant/ListeEvaluations"
import DetailEvaluation from "@/pages/etudiant/DetailEvaluation"
import PassationQCM from "@/pages/etudiant/passation/PassationQCM"
import ConfirmationSoumission from "@/pages/etudiant/passation/ConfirmationSoumission"
import ExpirationScreen from "@/pages/etudiant/passation/ExpirationScreen"
import ResultatsDetail from "@/pages/etudiant/ResultatsDetail"
import TableauBordPerformance from "@/pages/etudiant/TableauBordPerformance"
import { AdminLayout } from "@/layouts/AdminLayout";
import DashboardAdmin from "@/pages/admin/DashboardAdmin";

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Routes publiques */}
        <Route path="/login" element={<LoginPage />} />

        {/* Espace étudiant — navigation normale avec sidebar */}
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
          <Route path="resultats/:id" element={<ResultatsDetail />} />
          <Route path="performances" element={<TableauBordPerformance />} />
          {/* Futures pages étudiant :
              <Route path="resultats" element={<ResultatsDetail />} />
              <Route path="performances" element={<TableauBordPerformance />} />
              <Route path="notifications" element={<NotificationsPage />} /> */}
        </Route>

        {/* Mode examen — interface épurée, sans navigation */}
        <Route
          path="/examen"
          element={
            <ProtectedRoute allowedRoles={["ETUDIANT"]}>
              <ExamenLayout />
            </ProtectedRoute>
          }
        >
          <Route path=":id/qcm" element={<PassationQCM />} />
          <Route path=":id/confirmation" element={<ConfirmationSoumission />} />
          <Route path=":id/expiration" element={<ExpirationScreen />} />
          {/* Futures passations :
              <Route path=":id/ouverte" element={<PassationOuverte />} />
              <Route path=":id/code" element={<PassationCode />} />
              <Route path=":id/quiz" element={<PassationQuiz />} /> */}
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
          path="/admin"
          element={
            <ProtectedRoute allowedRoles={["ADMIN"]}>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardAdmin />} />
        </Route>

        {/* Par défaut → login */}
        <Route path="/" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}