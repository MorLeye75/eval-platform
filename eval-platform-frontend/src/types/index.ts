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
}

export interface SauvegardeResponse {
  dateSauvegarde: string // ISO 8601
  message: string
  nbReponses: number
  versionReponses: number
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
