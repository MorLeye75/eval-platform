import { expect, test, type APIRequestContext, type Page } from "@playwright/test"

/**
 * Affichage sur téléphone : aucune page ne doit être plus large que l'écran. Sinon le navigateur mobile affiche toute
 * la page en version bureau rétrécie, illisible. Les tableaux larges défilent dans leur cadre, pas la page.
 */
const LARGEUR_TELEPHONE = 390

test.use({ viewport: { width: LARGEUR_TELEPHONE, height: 844 }, isMobile: true, hasTouch: true })

async function connecter(page: Page, email: string, motDePasse: string) {
  await page.goto("/login")
  await page.getByLabel("Email").fill(email)
  await page.getByLabel("Mot de passe").fill(motDePasse)
  await page.getByRole("button", { name: "Se connecter" }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

/** Largeur réelle du document, une fois les données chargées. */
async function largeurDocument(page: Page) {
  await page.waitForLoadState("networkidle")
  await page.waitForTimeout(300)
  return page.evaluate(() => document.documentElement.scrollWidth)
}

async function verifierPages(page: Page, chemins: string[]) {
  const debordements: string[] = []
  for (const chemin of chemins) {
    await page.goto(chemin)
    const largeur = await largeurDocument(page)
    if (largeur > LARGEUR_TELEPHONE) debordements.push(`${chemin} (${largeur}px)`)
  }
  expect(debordements, "pages plus larges que l'écran du téléphone").toEqual([])
}

test("étudiant : accueil, évaluations, notifications et profil tiennent dans l'écran", async ({ page }) => {
  await connecter(page, "etudiant@ept.sn", "etudiant123")
  await verifierPages(page, ["/etudiant/dashboard", "/etudiant/evaluations", "/etudiant/notifications",
    "/etudiant/profil", "/etudiant/performances"])
})

/** Backend des tests de bout en bout (même port que playwright.config.ts). */
const API = "http://localhost:18082/api"

/**
 * Étudiant neuf de la promotion des évaluations de démonstration, créé par l'administrateur via l'API : le compte de
 * démonstration a déjà remis ses épreuves quand parcours-etudiant passe avant ce fichier.
 */
async function nouvelEtudiant(request: APIRequestContext) {
  const jeton = async (email: string, motDePasse: string) =>
    (await (await request.post(`${API}/auth/login`, { data: { email, motDePasse } })).json()).token as string
  const admin = { Authorization: `Bearer ${await jeton("admin@ept.sn", "admin123")}` }
  const promotions: { id: number; nom: string }[] = await (await request.get(`${API}/promotions`, { headers: admin })).json()
  const promotion = promotions.find((p) => p.nom === "DIC2 Informatique")!
  const email = `mobile-${Date.now()}@ept.sn`
  const creation = await request.post(`${API}/admin/utilisateurs`, {
    headers: admin,
    data: { nom: "Sarr", prenom: "Fatou", email, role: "ETUDIANT", promotionId: promotion.id, numEtudiant: `M-${Date.now()}` },
  })
  expect(creation.status()).toBe(201)
  const provisoire: string = (await creation.json()).motDePasseProvisoire
  // Mot de passe provisoire : à remplacer avant tout autre usage (DS-16)
  const motDePasse = "MotDePasseMobile1"
  const changement = await request.put(`${API}/profil/mot-de-passe`, {
    headers: { Authorization: `Bearer ${await jeton(email, provisoire)}` },
    data: { motDePasseActuel: provisoire, nouveauMotDePasse: motDePasse },
  })
  expect(changement.ok()).toBe(true)
  return { email, motDePasse }
}

test("étudiant : l'écran d'épreuve tient dans l'écran", async ({ page, request }) => {
  const etudiant = await nouvelEtudiant(request)
  await connecter(page, etudiant.email, etudiant.motDePasse)
  await page.goto("/etudiant/evaluations")
  const carte = page.locator('[data-slot="card"]').filter({ hasText: "Structures de données — QCM" })
  await carte.getByRole("link", { name: /Démarrer|Reprendre/ }).click()
  await page.getByRole("button", { name: /Démarrer l'évaluation|Reprendre l'évaluation/ }).click()
  await expect(page).toHaveURL(/\/epreuve/)
  expect(await largeurDocument(page)).toBeLessThanOrEqual(LARGEUR_TELEPHONE)
})

test("enseignant : toutes les pages de l'espace tiennent dans l'écran", async ({ page }) => {
  await connecter(page, "prof@ept.sn", "prof123")
  await verifierPages(page, ["/enseignant", "/enseignant/evaluations", "/enseignant/evaluations/creation",
    "/enseignant/evaluations/creation/questions", "/enseignant/statistiques", "/enseignant/supervision",
    "/enseignant/validation-ia", "/enseignant/publication", "/enseignant/export", "/enseignant/notifications"])
})

test("administrateur : toutes les pages de l'espace tiennent dans l'écran", async ({ page }) => {
  await connecter(page, "admin@ept.sn", "admin123")
  await verifierPages(page, ["/admin", "/admin/comptes", "/admin/supervision", "/admin/statistiques",
    "/admin/parametres"])
})

test("Mes évaluations sur téléphone : une carte par évaluation, avec ses actions, pas de tableau", async ({ page }) => {
  await connecter(page, "prof@ept.sn", "prof123")
  await page.goto("/enseignant/evaluations")
  const evaluation = page.getByRole("article", { name: "Structures de données — QCM" })
  await expect(evaluation).toBeVisible()
  await expect(evaluation.getByRole("link", { name: /Statistiques|Supervision/ }).first()).toBeVisible()
  await expect(page.getByRole("table")).toHaveCount(0)
})

test("Gestion des comptes sur téléphone : une carte par compte, avec ses actions, pas de tableau", async ({ page }) => {
  await connecter(page, "admin@ept.sn", "admin123")
  await page.goto("/admin/comptes")
  await expect(page.getByRole("article").first()).toBeVisible()
  await expect(page.getByRole("button", { name: /^Modifier :/ }).first()).toBeVisible()
  await expect(page.getByRole("table")).toHaveCount(0)
})
