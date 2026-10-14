import { lazy, Suspense } from "react"
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { ProtectedRoute } from "./ProtectedRoute"
import { useAuth } from "@/context/AuthContext"
import { accueilPour } from "@/lib/roles"
import { PageLoader } from "@/components/PageLoader"
import { EtudiantLayout } from "@/layouts/EtudiantLayout"
import { ExamenLayout } from "@/layouts/ExamenLayout"
import { EnseignantLayout } from "@/layouts/EnseignantLayout"
import { AdminLayout } from "@/layouts/AdminLayout"

// Lazy-loaded pages to enable code splitting and reduce initial bundle size
const LoginPage = lazy(() => import("@/pages/LoginPage"))
const ForgotPasswordPage = lazy(() => import("@/pages/ForgotPasswordPage"))
const ResetPasswordPage = lazy(() => import("@/pages/ResetPasswordPage"))

// Étudiant
const DashboardEtudiant = lazy(() => import("@/pages/etudiant/DashboardEtudiant"))
const ListeEvaluations = lazy(() => import("@/pages/etudiant/ListeEvaluations"))
const DetailEvaluation = lazy(() => import("@/pages/etudiant/DetailEvaluation"))
const ResultatsDetail = lazy(() => import("@/pages/etudiant/ResultatsDetail"))
const TableauBordPerformance = lazy(() => import("@/pages/etudiant/TableauBordPerformance"))
const NotificationsPage = lazy(() => import("@/pages/etudiant/NotificationsPage"))

// Passation
const PassationQCM = lazy(() => import("@/pages/etudiant/passation/PassationQCM"))
const PassationOuverte = lazy(() => import("@/pages/etudiant/passation/PassationOuverte"))
const PassationCode = lazy(() => import("@/pages/etudiant/passation/PassationCode"))
const PassationQuiz = lazy(() => import("@/pages/etudiant/passation/PassationQuiz"))
const ConfirmationSoumission = lazy(() => import("@/pages/etudiant/passation/ConfirmationSoumission"))
const ExpirationScreen = lazy(() => import("@/pages/etudiant/passation/ExpirationScreen"))

// Enseignant
const DashboardEnseignant = lazy(() => import("@/pages/enseignant/DashboardEnseignant"))
const ListeEvaluationsEnseignant = lazy(() => import("@/pages/enseignant/ListeEvaluationsEnseignant"))
const InfosGenerales = lazy(() => import("@/pages/enseignant/creation/InfosGenerales"))
const BuilderQuestions = lazy(() => import("@/pages/enseignant/creation/BuilderQuestions"))
const Previsualisation = lazy(() => import("@/pages/enseignant/creation/Previsualisation"))
const SupervisionTempsReel = lazy(() => import("@/pages/enseignant/SupervisionTempsReel"))
const ValidationIA = lazy(() => import("@/pages/enseignant/ValidationIA"))
const StatistiquesEvaluation = lazy(() => import("@/pages/enseignant/StatistiquesEvaluation"))
const PublicationResultats = lazy(() => import("@/pages/enseignant/PublicationResultats"))
const ExportResultats = lazy(() => import("@/pages/enseignant/ExportResultats"))

// Partagé
const NotificationsShared = lazy(() => import("@/pages/NotificationsPage"))
const ProfilePage = lazy(() => import("@/pages/ProfilePage"))

// Admin
const DashboardAdmin = lazy(() => import("@/pages/admin/DashboardAdmin"))
const GestionComptes = lazy(() => import("@/pages/admin/GestionComptes"))
const SupervisionGlobale = lazy(() => import("@/pages/admin/SupervisionGlobale"))
const StatistiquesGlobales = lazy(() => import("@/pages/admin/StatistiquesGlobales"))
const ParametresSysteme = lazy(() => import("@/pages/admin/ParametresSysteme"))

function IndexRedirect() {
  const { user, isAuthenticated } = useAuth()
  if (!isAuthenticated || !user) return <Navigate to="/login" replace />
  return <Navigate to={accueilPour(user.role)} replace />
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Suspense fallback={<PageLoader />}>
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
            <Route index element={<Navigate to="/etudiant/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardEtudiant />} />
            <Route path="evaluations" element={<ListeEvaluations />} />
            <Route path="evaluations/:id" element={<DetailEvaluation />} />
            <Route path="resultats/:id" element={<ResultatsDetail />} />
            <Route path="performances" element={<TableauBordPerformance />} />
            <Route path="notifications" element={<NotificationsPage />} />
            <Route path="profil" element={<ProfilePage />} />
          </Route>

          {/* Mode examen — interface épurée sans navigation latérale */}
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
            <Route path=":id/quiz" element={<PassationQuiz />} />
            <Route path=":id/confirmation" element={<ConfirmationSoumission />} />
            <Route path=":id/expiration" element={<ExpirationScreen />} />
          </Route>

          {/* Espace enseignant */}
          <Route
            path="/enseignant"
            element={
              <ProtectedRoute allowedRoles={["ENSEIGNANT"]}>
                <EnseignantLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<DashboardEnseignant />} />
            <Route path="evaluations" element={<ListeEvaluationsEnseignant />} />
            <Route path="evaluations/creation" element={<InfosGenerales />} />
            <Route path="evaluations/creation/questions" element={<BuilderQuestions />} />
            <Route path="evaluations/creation/previsualisation" element={<Previsualisation />} />
            {/* TODO : page d'édition dédiée — réutilise l'étape 1 en attendant */}
            <Route path="evaluations/:id/edition" element={<InfosGenerales />} />
            <Route path="supervision/:id?" element={<SupervisionTempsReel />} />
            <Route path="validation-ia" element={<ValidationIA />} />
            <Route path="statistiques/:id?" element={<StatistiquesEvaluation />} />
            <Route path="publication" element={<PublicationResultats />} />
            <Route path="export" element={<ExportResultats />} />
            <Route path="notifications" element={<NotificationsShared />} />
            <Route path="profil" element={<ProfilePage />} />
          </Route>

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
            <Route path="comptes" element={<GestionComptes />} />
            <Route path="supervision" element={<SupervisionGlobale />} />
            <Route path="statistiques" element={<StatistiquesGlobales />} />
            <Route path="parametres" element={<ParametresSysteme />} />
            <Route path="profil" element={<ProfilePage />} />
          </Route>

          {/* Par défaut → redirection intelligente selon statut et rôle */}
          <Route path="/" element={<IndexRedirect />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}