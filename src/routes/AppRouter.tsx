import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { ProtectedRoute } from "./ProtectedRoute"
import LoginPage from "@/pages/LoginPage"

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Routes publiques */}
        <Route path="/login" element={<LoginPage />} />

        {/* Espace étudiant */}
        <Route
          path="/etudiant/*"
          element={
            <ProtectedRoute allowedRoles={["ETUDIANT"]}>
              <div>Espace étudiant — pages à venir</div>
            </ProtectedRoute>
          }
        />

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