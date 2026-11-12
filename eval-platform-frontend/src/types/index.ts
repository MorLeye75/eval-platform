// ============================================================
// Types partagés — reflètent le diagramme de classes UML
// Contrat commun front / back (Spring Boot)
// ============================================================

// ---------- Utilisateur & rôles ----------
export type Role = "ETUDIANT" | "ENSEIGNANT" | "ADMIN"

export interface Utilisateur {
  id: number
  nom: string
  prenom: string
  email: string
  role: Role
}

/** GET /profil : identité en lecture seule ; matricule et promotion pour un étudiant seulement. */
export interface Profil extends Utilisateur {
  numEtudiant?: string
  promotion?: string
}

// ---------- Énumérations (d'après le diagramme de classes) ----------
export type StatutEvaluation =
  | "BROUILLON"
  | "PUBLIEE"
  | "EN_COURS"
  | "TERMINEE"

export type StatutSoumission =
  | "EN_COURS"
  | "SOUMIS"
  | "EN_CORRECTION"
  | "EVALUE"

export type StatutProposition = "EN_ATTENTE" | "VALIDEE" | "REJETEE"

export type TypeQuestion = "QCM" | "CODE" | "OUVERTE" | "QUIZ_CHRONOMETREE"

/** GET /etudiant/evaluations : évaluation vue par l'étudiant, avec l'état de sa copie. */
export interface EvaluationEtudiant {
  id: number
  titre: string
  description: string | null
  statut: StatutEvaluation
  dateOuverture: string
  dateFermeture: string
  dureeMinutes: number // tiers-temps de l'étudiant s'il existe
  dureeQuestion: number | null // secondes par question (quiz chronométré)
  nbQuestions: number
  totalPoints: number
  typesQuestions: TypeQuestion[]
  resultatsPublies: boolean
  soumissionId: number | null
  statutSoumission: StatutSoumission | null
  noteFinale: number | null // seulement si les résultats sont publiés
}

// ---------- Évaluation ----------
export interface Evaluation {
  id: number
  titre: string
  description: string
  duree: number // en minutes
  dateOuverture: string // ISO 8601 (ex: "2026-07-12T08:00:00")
  dateFermeture: string
  statut: StatutEvaluation
  resultatsPublies: boolean
  questions?: Question[] // chargées selon le contexte
}

// ---------- Question (abstraite) et ses variantes ----------
export interface QuestionBase {
  id: number
  enonce: string
  ordre: number
  points: number
  type: TypeQuestion
}

export interface ChoixReponse {
  id: number
  texte: string
  estCorrect: boolean // visible côté enseignant uniquement
}

export interface QuestionQCM extends QuestionBase {
  type: "QCM"
  estChoixMultiple: boolean
  choix: ChoixReponse[]
}

export interface QuestionOuverte extends QuestionBase {
  type: "OUVERTE"
}

export interface CasTest {
  id: number
  entree: string
  sortieAttendue: string
  estMasque: boolean // cas de test caché à l'étudiant
}

export interface QuestionCode extends QuestionBase {
  type: "CODE"
  casTests: CasTest[]
}

export interface QuestionQuiz extends QuestionBase {
  type: "QUIZ_CHRONOMETREE"
  dureeQuestion: number // durée individuelle en secondes
}

// Union pratique pour manipuler n'importe quelle question
export type Question =
  | QuestionQCM
  | QuestionOuverte
  | QuestionCode
  | QuestionQuiz

// ---------- Soumission & réponses ----------
export interface Soumission {
  id: number
  evaluationId: number
  etudiantId: number
  dateDebut: string
  dateRemise: string | null
  noteFinale: number | null
  statut: StatutSoumission
  feedbackGeneral: string | null
}

export interface ReponseEtudiant {
  id: number
  questionId: number
  noteObtenue: number | null
  valideParProf: boolean
}

// ---------- Passation (DS-01 soumission, DS-02 auto-save) ----------
// Copie vue par l'étudiant : jamais de bonne réponse ni de cas de test masqué

export interface ReponseItem {
  questionId: number
  texteSoumis?: string
  codeSoumis?: string
  langage?: string | null // réponse de code : c, cpp, java, python, javascript
  choixIds?: number[]
}

export interface QuestionExamen {
  id: number
  enonce: string
  ordre: number
  points: number
  type: TypeQuestion
  estChoixMultiple: boolean | null
  choixPossibles: { id: number; texte: string }[]
  casDeTestsVisibles: { id: number; entree: string; sortieAttendue: string }[]
  reponseActuelle: ReponseItem | null
  dureeQuestion?: number | null // secondes (quiz chronométré)
}

/** Quiz chronométré : position de la copie (le serveur n'envoie que la question en cours). */
export interface EtatQuiz {
  indexQuestion: number // = nbQuestions quand le quiz est terminé
  nbQuestions: number
  dureeQuestion: number | null
  tempsRestantQuestionSecondes: number
}

export interface CopieEnCours {
  id: number
  evaluationId: number
  titreEvaluation: string
  descriptionEvaluation: string | null
  statut: StatutSoumission
  dateDebut: string
  dateRemise: string | null
  versionReponses: number // incrémentée à chaque sauvegarde réussie côté serveur
  dureeMinutes: number
  tempsRestantSecondes: number
  nbChangementsOnglet: number
  questions: QuestionExamen[]
  quiz?: EtatQuiz | null
}

export interface SauvegardeResponse {
  dateSauvegarde: string // ISO 8601
  message: string
  nbReponses: number
  versionReponses: number
  tempsRestantSecondes: number // temps restant selon le serveur : le chrono s'y recale
}

// ---------- Correction IA ----------
export interface PropositionIA {
  id: number
  noteProposee: number
  justification: string
  statut: StatutProposition
}

export interface ResultatCasTest {
  id: number
  sortieObtenue: string
  tempsExecution: number
  estValide: boolean
  messageErreur: string | null
}

// ---------- Résultats de l'étudiant (DS-13) ----------
export type EtatResultats = "NON_REMISE" | "EN_ATTENTE" | "PUBLIES"

export type VerdictCas =
  | "REUSSI"
  | "MAUVAISE_REPONSE"
  | "TEMPS_DEPASSE"
  | "ERREUR_COMPILATION"
  | "ERREUR_EXECUTION"

/** estCorrect : null tant que le corrigé n'est pas visible (avant la fermeture de l'évaluation). */
export interface ChoixResultat {
  id: number
  texte: string
  coche: boolean
  estCorrect: boolean | null
}

/** Cas masqué : verdict et temps seulement, les autres champs sont null. */
export interface CasTestResultat {
  masque: boolean
  reussi: boolean | null // null : cas masqué avant la fermeture + 2 min (verdict pas encore communiqué)
  verdict: VerdictCas | null
  tempsExecution: number | null
  entree: string | null
  sortieAttendue: string | null
  sortieObtenue: string | null
  message: string | null
}

export interface ResultatQuestion {
  questionId: number
  ordre: number
  type: TypeQuestion
  enonce: string
  points: number
  noteObtenue: number | null
  commentaire: string | null
  choix: ChoixResultat[]
  texteSoumis: string | null
  codeSoumis: string | null
  langage: string | null
  casTests: CasTestResultat[]
}

/** Hors état PUBLIES : aucune note, questions vides. Moyenne null sous 5 copies notées. */
export interface ResultatsCopie {
  etat: EtatResultats
  soumissionId: number
  evaluationId: number
  titre: string
  dateRemise: string | null
  noteFinale: number | null
  totalPoints: number | null
  feedbackGeneral: string | null
  moyennePromotion: number | null
  ecartMoyenne: number | null
  corrigeVisible: boolean
  questions: ResultatQuestion[]
}

export interface PointPerformance {
  soumissionId: number
  evaluationId: number
  titre: string
  date: string | null
  noteSur20: number
  moyennePromotionSur20: number | null
}

/** ecartMoyenne : calculé seulement sur les évaluations dont la moyenne de la promotion est connue. */
export interface Performances {
  historique: PointPerformance[]
  moyenneGenerale: number | null
  moyennePromotionGenerale: number | null
  ecartMoyenne: number | null
  nbEvaluations: number
}

// ---------- Analytique (vues) ----------
export interface StatistiquesEvaluation {
  moyenne: number
  noteMax: number
  noteMin: number
  ecartType: number
  tauxReussite: number
}

export interface TableauDeBordEtudiant {
  moyenneGenerale: number
  tauxReussite: number
  rang: number
}

// ---------- Espace Admin ----------


// ---------- Statut compte (§3.3 du CDC) ----------
// "Suspendu" retiré en attente de décision avec l'encadrant
export type StatutCompte = "ACTIF" | "DESACTIVE"

export interface UtilisateurAdmin extends Utilisateur {
  actif: boolean           // false = désactivé (StatutCompte)
  dateCreation: string     // ISO 8601
  derniereConnexion: string | null
}

// ---------- Stats globales (vue admin) ----------
export interface StatistiquesGlobales {
  moyennePlateforme: number
  tauxReussite: number      // pourcentage
  nbEvaluationsCreees: number
  tauxCompletion: number    // pourcentage
  nbUtilisateurs: number
  nbEvaluationsActives: number
  nbSoumissions7j: number
  nbIncidents: number
}

// ---------- Évaluation en cours (supervision) ----------
export interface EvaluationEnCours {
  id: number
  titre: string
  enseignant: Pick<Utilisateur, "nom" | "prenom">  // relation Enseignant
  nbSoumis: number         // soumissions reçues
  nbAttendus: number       // total inscrits
  dateDebut: string        // ISO 8601
  dateFin: string          // ISO 8601
}

// ---------- Alerte anti-triche ----------
export type NiveauAlerte = "INFO" | "MODERE" | "ELEVE"

export interface AlerteAntiTriche {
  id: number
  etudiantId: number
  evaluationId: number
  description: string
  niveau: NiveauAlerte
  date: string             // ISO 8601
}

// ---------- Notifications ----------
export type TypeNotification = "RESULTAT_PUBLIE" | "EVALUATION_OUVERTE" | "CORRECTION_VALIDEE"
