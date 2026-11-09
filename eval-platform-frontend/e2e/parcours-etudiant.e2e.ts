import { expect, test, type Page } from "@playwright/test"

/**
 * Parcours étudiant de bout en bout, sans aucune simulation : navigateur, frontend, backend et base H2 réels
 * (données de démonstration). Les scénarios s'enchaînent sur les copies du même étudiant, dans l'ordre.
 */
test.describe.configure({ mode: "serial" })

const QCM = "Structures de données — QCM"
const CODE = "Algorithmique — Exercice de code"
const QUIZ = "Bases de données — Quiz chronométré"

async function connecter(page: Page, email = "etudiant@ept.sn", motDePasse = "etudiant123") {
  await page.goto("/login")
  await page.getByLabel("Email").fill(email)
  await page.getByLabel("Mot de passe").fill(motDePasse)
  await page.getByRole("button", { name: "Se connecter" }).click()
  // Connexion aboutie : le front a quitté /login (jeton enregistré) avant toute autre navigation
  await expect(page).not.toHaveURL(/\/login/)
}

/** Depuis la liste, ouvre le détail d'une évaluation puis démarre (ou reprend) l'épreuve. */
async function demarrer(page: Page, titre: string) {
  await page.goto("/etudiant/evaluations")
  const carte = page.locator('[data-slot="card"]').filter({ hasText: titre })
  await carte.getByRole("link", { name: /Démarrer|Reprendre/ }).click()
  await expect(page.getByRole("heading", { name: titre })).toBeVisible()
  await page.getByRole("button", { name: /Démarrer l'évaluation|Reprendre l'évaluation/ }).click()
}

test("connexion : l'étudiant arrive sur son accueil, sans données simulées", async ({ page }) => {
  await connecter(page)

  await expect(page).toHaveURL(/\/etudiant\/dashboard$/)
  await expect(page.getByRole("heading", { name: /Bonjour, Aminou/ })).toBeVisible()
  // Les trois évaluations de démonstration viennent du serveur
  for (const titre of [QCM, CODE, QUIZ]) {
    await expect(page.getByText(titre)).toBeVisible()
  }
})

test("QCM : réponse sauvegardée côté serveur et retrouvée après rechargement, puis remise", async ({ page }) => {
  await connecter(page)
  await demarrer(page, QCM)

  // Question 1 (choix unique) : la bonne réponse
  await page.getByText("Pile (Stack)").click()
  await expect(page.getByText("1 / 2 répondues")).toBeVisible()
  // La sauvegarde automatique ne part qu'au bout de 30 s ; changer de question n'attend pas : on recharge
  // après la sauvegarde confirmée par le serveur
  await expect(page.getByText(/Sauvegardé à \d{2}:\d{2}/)).toBeVisible({ timeout: 45_000 })
  await page.reload()
  await expect(page.getByText("1 / 2 répondues")).toBeVisible()
  await expect(page.getByRole("radio", { name: "Pile (Stack)" })).toBeChecked()

  // Question 2 (choix multiples) puis remise
  await page.getByRole("button", { name: "Suivant" }).click()
  await page.getByText("Tableau", { exact: true }).click()
  await page.getByText("Liste chaînée", { exact: true }).click()
  await page.getByText("File", { exact: true }).click()
  await expect(page.getByText("2 / 2 répondues")).toBeVisible()
  await page.getByRole("button", { name: "Soumettre" }).click()
  await expect(page.getByRole("heading", { name: "Confirmer la soumission" })).toBeVisible()
  await page.getByRole("dialog").getByRole("button", { name: "Soumettre" }).click()

  await expect(page.getByRole("heading", { name: "Évaluation soumise" })).toBeVisible()
})

test("résultats : copie remise mais non publiée, aucune note affichée (DS-13)", async ({ page }) => {
  await connecter(page)

  // L'accueil signale la copie remise en attente des résultats
  await expect(page.getByText("Copie remise · en attente des résultats")).toBeVisible()
  await page.goto("/etudiant/evaluations")
  const carte = page.locator('[data-slot="card"]').filter({ hasText: QCM })
  await expect(carte.getByText("En attente des résultats")).toBeVisible()

  // Tableau de bord : rien n'est publié
  await page.goto("/etudiant/performances")
  await expect(page.getByText("Pas encore de résultats publiés")).toBeVisible()
})

test("quiz chronométré : une question à la fois, sans retour en arrière, imposé par le serveur", async ({ page }) => {
  await connecter(page)
  await demarrer(page, QUIZ)

  await expect(page.getByText(/TRUNCATE TABLE/)).toBeVisible()
  await expect(page.getByRole("button", { name: /Précédent/ })).toHaveCount(0)
  await page.getByText("TRUNCATE TABLE").click()
  await page.getByRole("button", { name: /Question suivante/ }).click()

  // Deuxième question servie par le serveur ; la première n'est plus affichée
  await expect(page.getByText("Quelles propriétés font partie de ACID ?")).toBeVisible()
  await expect(page.getByText("TRUNCATE TABLE")).toHaveCount(0)

  // Un rechargement reprend sur la question en cours, pas au début
  await page.reload()
  await expect(page.getByText("Quelles propriétés font partie de ACID ?")).toBeVisible()
})

test("code : le programme est saisi dans l'éditeur, enregistré avec son langage, puis la copie est remise", async ({ page }) => {
  await connecter(page)
  await demarrer(page, CODE)

  const editeur = page.locator(".monaco-editor").first()
  await expect(editeur).toBeVisible({ timeout: 30_000 }) // Monaco est chargé depuis un CDN
  // Frappe avant le choix du langage : en mode Python, les complétions automatiques de l'éditeur
  // s'appliqueraient au clavier simulé
  await editeur.click()
  await page.keyboard.type("s = input(); print('true' if s == s[::-1] else 'false')")
  await expect(page.locator(".view-lines")).toContainText("print('true' if s == s[::-1] else 'false')")
  // Le langage proposé par défaut est le C : l'étudiant choisit Python
  await page.getByRole("combobox").click()
  await page.getByRole("option", { name: "Python" }).click()

  // Sauvegarde confirmée par le serveur (le tampon local est alors vidé) : après rechargement, le code
  // et le langage reviennent du serveur
  await expect(page.getByText(/Sauvegardé à \d{2}:\d{2}/)).toBeVisible({ timeout: 45_000 })
  await page.reload()
  await expect(page.locator(".view-lines")).toContainText("print('true' if s == s[::-1] else 'false')", { timeout: 30_000 })
  // Libellé (« Python »), pas l'identifiant brut (« python ») : la liste n'a pas encore été ouverte
  await expect(page.getByRole("combobox")).toContainText("Python")

  await page.getByRole("button", { name: "Soumettre" }).click()
  await page.getByRole("dialog").getByRole("button", { name: "Soumettre" }).click()
  await expect(page.getByRole("heading", { name: "Évaluation soumise" })).toBeVisible()
})

test("cloisonnement des rôles : un enseignant ne peut pas ouvrir l'espace étudiant", async ({ page }) => {
  await connecter(page, "prof@ept.sn", "prof123")
  await expect(page).toHaveURL(/\/enseignant/)

  await page.goto("/etudiant/evaluations")
  await expect(page).toHaveURL(/\/enseignant/)
})
