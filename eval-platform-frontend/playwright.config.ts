import { defineConfig, devices } from "@playwright/test"

/**
 * Tests de bout en bout : vrai navigateur, vrai frontend, vrai backend, vraie base (H2 neuve à chaque exécution).
 * Aucune simulation. Prérequis : le jar du backend construit (cd eval-platform-backend && mvn package -DskipTests).
 * Ports dédiés (18082, 5174) : n'interfère pas avec l'environnement de développement (8080, 5173).
 */
const PORT_API = 18082
const PORT_FRONT = 5174

export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.e2e.ts",
  // Un seul étudiant de démonstration : les scénarios s'enchaînent sur ses copies, dans l'ordre
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // En CI, chaque échec devient aussi une annotation du run GitHub (fichier, ligne, message)
  reporter: process.env.CI ? [["github"], ["list"]] : [["list"]],
  use: {
    baseURL: `http://localhost:${PORT_FRONT}`,
    locale: "fr-FR",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: "java -jar ../eval-platform-backend/target/backend-0.0.1-SNAPSHOT.jar",
      env: {
        SPRING_PROFILES_ACTIVE: "h2-dev",
        SERVER_PORT: String(PORT_API),
        CORS_ALLOWED_ORIGINS: `http://localhost:${PORT_FRONT}`,
        // Judge0 injoignable : la correction du code est retentée plus tard, la remise n'en dépend pas
        JUDGE0_URL: "http://127.0.0.1:2399",
      },
      // 401 tant qu'on n'est pas connecté : le serveur est prêt
      url: `http://localhost:${PORT_API}/api/auth/me`,
      timeout: 120_000,
      reuseExistingServer: false,
      stdout: "ignore",
    },
    {
      // Version de production (build puis preview), comme au déploiement : pas de recompilation à la volée,
      // ni de conflit de cache avec un serveur Vite de développement déjà lancé
      command: `npx vite build --outDir dist-e2e --emptyOutDir && npx vite preview --outDir dist-e2e --port ${PORT_FRONT} --strictPort`,
      env: { VITE_API_URL: `http://localhost:${PORT_API}/api` },
      url: `http://localhost:${PORT_FRONT}`,
      timeout: 180_000,
      reuseExistingServer: false,
    },
  ],
})
