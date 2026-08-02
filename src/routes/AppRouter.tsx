import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { ProtectedRoute } from "./ProtectedRoute"
import LoginPage from "@/pages/LoginPage"
import ForgotPasswordPage from "@/pages/ForgotPasswordPage"
import ResetPasswordPage from "@/pages/ResetPasswordPage"
import { EtudiantLayout } from "@/layouts/EtudiantLayout"
import { ExamenLayout } from "@/layouts/ExamenLayout"
import DashboardEtudiant from "@/pages/etudiant/DashboardEtudiant"
import ListeEvaluations from "@/pages/etudiant/ListeEvaluations"
import DetailEvaluation from "@/pages/etudiant/DetailEvaluation"
import PassationQCM from "@/pages/etudiant/passation/PassationQCM"
import PassationOuverte from "@/pages/etudiant/passation/PassationOuverte"
import PassationCode from "@/pages/etudiant/passation/PassationCode"
import ConfirmationSoumission from "@/pages/etudiant/passation/ConfirmationSoumission"
import ExpirationScreen from "@/pages/etudiant/passation/ExpirationScreen"
import ResultatsDetail from "@/pages/etudiant/ResultatsDetail"
import TableauBordPerformance from "@/pages/etudiant/TableauBordPerformance"
import ListeEvaluationsEnseignant from "@/pages/enseignant/ListeEvaluationsEnseignant"
import InfosGenerales from "@/pages/enseignant/creation/InfosGenerales"
import BuilderQuestions from "@/pages/enseignant/creation/BuilderQuestions"
import Previsualisation from "@/pages/enseignant/creation/Previsualisation"
import NotificationsPage from "@/pages/NotificationsPage"
import ProfilePage from "@/pages/ProfilePage"


export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Routes publiques */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/reset-password/:token" element={<ResetPasswordPage />} />

        {/* Espace étudiant — navigation normale avec sidebar */}
        <Route
          path="/etudiant"
          element={
            <ProtectedRoute allowedRoles={["ETUDIANT"]}>
              <EtudiantLayout />
            </ProtectedRoute>
          }
        >
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profil" element={<ProfilePage />} />
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
          <Route path=":id/ouverte" element={<PassationOuverte />} />
          <Route path=":id/code" element={<PassationCode />} />
          <Route path=":id/confirmation" element={<ConfirmationSoumission />} />
          <Route path=":id/expiration" element={<ExpirationScreen />} />
          {/* Futures passations :
              <Route path=":id/quiz" element={<PassationQuiz />} /> */}
        </Route>

        {/* Espace enseignant */}
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="profil" element={<ProfilePage />} />
        <Route path="evaluations" element={<ListeEvaluationsEnseignant />} />
        <Route path="evaluations/creation" element={<InfosGenerales />} />
        <Route path="evaluations/creation/questions" element={<BuilderQuestions />} />
        <Route path="evaluations/creation/previsualisation" element={<Previsualisation />} />

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