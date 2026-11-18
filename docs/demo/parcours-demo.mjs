// Parcours de démonstration de bout en bout, par l'API (voir docs/demo/README.md).
// Usage : node docs/demo/parcours-demo.mjs [http://localhost:8080/api]
// Prérequis : backend lancé en profil h2-dev (comptes de démonstration du README racine).
const BASE = process.argv[2] ?? "http://localhost:8080/api"
const etapes = []
let n = 0

async function appel(jeton, methode, chemin, corps) {
  const r = await fetch(BASE + chemin, {
    method: methode,
    headers: { "Content-Type": "application/json", ...(jeton ? { Authorization: `Bearer ${jeton}` } : {}) },
    body: corps === undefined ? undefined : JSON.stringify(corps),
  })
  const texte = await r.text()
  let donnees = null
  try { donnees = texte ? JSON.parse(texte) : null } catch { donnees = texte }
  return { statut: r.status, donnees }
}

function verifier(titre, ok, detail = "") {
  n += 1
  etapes.push({ ok })
  console.log(`${ok ? "OK  " : "ECHEC"} ${String(n).padStart(2, "0")} ${titre}${detail ? "  → " + detail : ""}`)
}
const titre = (t) => console.log(`\n=== ${t} ===`)

async function connexion(email, mdp) {
  const { statut, donnees } = await appel(null, "POST", "/auth/login", { email, motDePasse: mdp })
  if (statut !== 200) throw new Error(`connexion ${email} : ${statut}`)
  return donnees
}

const suffixe = Date.now().toString(36)

// ---------------------------------------------------------------- 1. Administrateur
titre("1. Administrateur : promotion et compte étudiant")
const admin = (await connexion("admin@ept.sn", "admin123")).token
let r = await appel(admin, "POST", "/admin/promotions", { nom: `Démo ${suffixe}`, annee: "2026-2027" })
verifier("créer une promotion", r.statut === 201, `id ${r.donnees?.id}`)
const promotionId = r.donnees.id
r = await appel(admin, "PUT", `/admin/promotions/${promotionId}`, { nom: `Démo ${suffixe} (renommée)`, annee: "2026-2027" })
verifier("renommer la promotion", r.statut === 200 && r.donnees.nom.includes("renommée"))

const email = `demo-${suffixe}@ept.sn`
r = await appel(admin, "POST", "/admin/utilisateurs", {
  nom: "Ndiaye", prenom: "Fatou", email, role: "ETUDIANT", promotionId, numEtudiant: `DEMO-${suffixe}`,
})
verifier("créer un étudiant : mot de passe provisoire renvoyé une fois", r.statut === 201 && r.donnees.motDePasseProvisoire?.length === 12)
const provisoire = r.donnees.motDePasseProvisoire
const etudiantDemoId = r.donnees.utilisateur.id

r = await appel(admin, "DELETE", `/admin/promotions/${promotionId}`)
verifier("supprimer une promotion qui a des étudiants → refusé", r.statut === 409, r.donnees?.detail)

// ---------------------------------------------------------------- 2. Premier accès de l'étudiant
titre("2. Étudiant : premier accès avec le mot de passe provisoire")
const premiere = await connexion(email, provisoire)
verifier("la connexion signale le mot de passe provisoire", premiere.motDePasseProvisoire === true)
r = await appel(premiere.token, "GET", "/etudiant/evaluations")
verifier("tout est refusé tant qu'il n'est pas changé (403)", r.statut === 403 && r.donnees?.code === "MOT_DE_PASSE_A_CHANGER")
r = await appel(premiere.token, "PUT", "/profil/mot-de-passe", { motDePasseActuel: provisoire, nouveauMotDePasse: "MotDePasseDemo2026" })
verifier("changer son mot de passe", r.statut === 204)
const apres = await connexion(email, "MotDePasseDemo2026")
verifier("nouvelle connexion : plus de contrainte", apres.motDePasseProvisoire === false)
r = await appel(apres.token, "GET", "/etudiant/evaluations")
verifier("accès à ses évaluations", r.statut === 200, `${r.donnees.length} évaluation(s) pour sa promotion`)

// ---------------------------------------------------------------- 3. Enseignant : création
titre("3. Enseignant : créer, aménager, publier")
const prof = (await connexion("prof@ept.sn", "prof123")).token
r = await appel(prof, "POST", "/enseignant/evaluations", {
  titre: `Réseaux — contrôle ${suffixe}`, description: "Contrôle de démonstration", dureeStandard: 60,
  dateOuverture: "2030-11-10T08:00:00", dateFermeture: "2030-11-10T12:00:00", promotionIds: [promotionId],
  questions: [
    { type: "QCM", enonce: "Quel protocole assure la livraison fiable ?", points: 2, estChoixMultiple: false,
      choix: [{ texte: "TCP", estCorrect: true }, { texte: "UDP", estCorrect: false }] },
    { type: "OUVERTE", enonce: "Expliquez le routage", points: 4 },
  ],
})
verifier("créer un brouillon", r.statut === 201, `id ${r.donnees?.id}`)
const evalId = r.donnees.id
r = await appel(prof, "GET", `/enseignant/evaluations/${evalId}/amenagements`)
verifier("liste des étudiants attendus", r.statut === 200 && r.donnees.etudiants.some((e) => e.etudiantId === etudiantDemoId))
r = await appel(prof, "PUT", `/enseignant/evaluations/${evalId}/amenagements/${etudiantDemoId}`, { dureeMinutes: 80 })
verifier("tiers-temps de 80 min", r.statut === 200)
r = await appel(prof, "PUT", `/enseignant/evaluations/${evalId}/amenagements/${etudiantDemoId}`, { dureeMinutes: 9999 })
verifier("tiers-temps hors plage → refusé", r.statut === 400 || r.statut === 409, r.donnees?.detail)
r = await appel(prof, "POST", `/enseignant/evaluations/${evalId}/publier`)
verifier("publier l'évaluation", r.statut === 200 && r.donnees.statut === "PUBLIEE", r.donnees?.statut)
r = await appel(apres.token, "GET", "/etudiant/evaluations")
verifier("l'étudiant la voit", r.donnees.some((e) => e.id === evalId))
r = await appel(prof, "POST", `/enseignant/evaluations/${evalId}/resultats/publier`)
verifier("publier des résultats avant la fin → refusé (409)", r.statut === 409)

// ---------------------------------------------------------------- 4. Étudiant : passation (évaluation de démonstration ouverte)
titre("4. Étudiant de démonstration : passation d'un QCM")
const etu = (await connexion("etudiant@ept.sn", "etudiant123")).token
r = await appel(etu, "POST", "/soumissions/evaluations/1/demarrer")
verifier("démarrer l'épreuve", r.statut === 200 || r.statut === 201, `copie ${r.donnees?.id}`)
const copie = r.donnees
const sujet = (await appel(etu, "GET", `/soumissions/${copie.id}`)).donnees
const questions = sujet.evaluation?.questions ?? sujet.questions ?? []
verifier("le sujet ne révèle aucune bonne réponse", !JSON.stringify(sujet).includes("estCorrect"))
const reponses = questions.map((q) => ({ questionId: q.id, choixIds: (q.choix ?? []).slice(0, 1).map((c) => c.id) }))
r = await appel(etu, "PUT", `/soumissions/${copie.id}/sauvegarder`, { reponses, versionAttendue: copie.versionReponses ?? 0 })
verifier("sauvegarde automatique", r.statut === 200 || r.statut === 204, `statut ${r.statut}`)
r = await appel(etu, "POST", `/soumissions/${copie.id}/changement-onglet`)
verifier("changement d'onglet signalé", r.statut === 200 || r.statut === 204)

// ---------------------------------------------------------------- 5. Enseignant : supervision puis résultats
titre("5. Enseignant : supervision, résultats, export")
r = await appel(prof, "GET", "/enseignant/supervision/1")
verifier("supervision en direct", r.statut === 200 && r.donnees.nbCommences >= 1, `${r.donnees?.nbCommences} commencé(s)`)
r = await appel(etu, "POST", `/soumissions/${copie.id}/soumettre`)
verifier("l'étudiant remet sa copie", r.statut === 200 || r.statut === 204, `statut ${r.statut}`)
r = await appel(prof, "GET", "/enseignant/evaluations/1/statistiques")
verifier("statistiques de l'évaluation", r.statut === 200)
r = await appel(prof, "GET", "/enseignant/evaluations/1/resultats")
verifier("notes par étudiant", r.statut === 200, `${r.donnees?.lignes?.length ?? r.donnees?.etudiants?.length ?? "?"} ligne(s)`)
r = await appel(prof, "GET", "/enseignant/evaluations/1/export/csv")
verifier("export CSV", r.statut === 200)

// ---------------------------------------------------------------- 6. Administrateur : vue d'ensemble
titre("6. Administrateur : vue d'ensemble")
r = await appel(admin, "GET", "/admin/tableau-de-bord")
verifier("tableau de bord", r.statut === 200, `${r.donnees?.nbUtilisateurs} utilisateurs, ${r.donnees?.nbEvaluationsEnCours} évaluation(s) en cours`)
r = await appel(admin, "GET", "/admin/supervision")
verifier("supervision globale", r.statut === 200, `${r.donnees?.evaluations?.length} évaluation(s) en cours`)
r = await appel(admin, "GET", "/admin/statistiques")
verifier("statistiques globales", r.statut === 200, `${r.donnees?.nbCopies} copie(s)`)
r = await appel(prof, "GET", "/admin/statistiques")
verifier("un enseignant n'a pas accès à l'administration (403)", r.statut === 403)

// ---------------------------------------------------------------- 7. Sécurité : réinitialisation
titre("7. Réinitialisation du mot de passe coupe la session")
const ancienne = (await connexion(email, "MotDePasseDemo2026")).token
await new Promise((ok) => setTimeout(ok, 1200))
r = await appel(admin, "POST", `/admin/utilisateurs/${etudiantDemoId}/reinitialiser-mot-de-passe`)
verifier("l'administrateur réinitialise le mot de passe", r.statut === 200)
r = await appel(ancienne, "GET", "/profil")
verifier("la session ouverte est coupée (401)", r.statut === 401)

const echecs = etapes.filter((e) => !e.ok).length
console.log(`\n${etapes.length - echecs}/${etapes.length} étapes réussies`)
process.exit(echecs ? 1 : 0)
