// ============================================================
// Données mockées centralisées — source unique de vérité
// À remplacer par les appels API (React Query) quand le backend sera prêt
// Règle métier : noteFinale = (pointsObtenus / pointsMax) × noteStandard
// ============================================================

export type StatutEval = "disponible" | "a_venir" | "terminee"
export type TypeEval = "QCM" | "CODE" | "OUVERTE" | "QUIZ"
export type TypeQuestionMock = "QCM" | "OUVERTE" | "CODE"

export interface ChoixMock {
  id: number
  texte: string
  estCorrect: boolean
}

export interface CasDeTestMock {
  id: number
  entree: string
  sortieAttendue: string
  estMasque: boolean // cas caché : non exécutable par l'étudiant, réservé à la correction
}

export interface QuestionMock {
  id: number
  enonce: string
  points: number
  type: TypeQuestionMock
  estChoixMultiple?: boolean // uniquement pour QCM
  choix?: ChoixMock[] // uniquement pour QCM
  consigne?: string // aide facultative pour les questions ouvertes/code
  langagesAutorises?: string[] // uniquement pour CODE
  casDeTests?: CasDeTestMock[] // uniquement pour CODE
}

export interface EvaluationMock {
  id: number
  titre: string
  description: string
  type: TypeEval
  duree: number // minutes
  dateLimite: string
  statut: StatutEval
  noteStandard: number // barème défini par l'enseignant
  questions: QuestionMock[]

  // Présent uniquement si l'évaluation est terminée
  resultat?: {
    resultatsPublies: boolean
    dateSoumission: string
    noteFinale: number
    pointsObtenus: number
    pointsMax: number
    moyennePromotion: number
    feedbackGeneral: string
    reponsesEtudiant: Record<number, number[]> // questionId -> choixIds choisis
    commentaires: Record<number, string> // questionId -> commentaire du prof
  }
}

export const evaluationsMock: EvaluationMock[] = [
  {
    id: 1,
    titre: "Structures de données — QCM",
    description:
      "Évaluation portant sur les listes, piles, files, arbres et tables de hachage. Lisez attentivement chaque question avant de répondre.",
    type: "QCM",
    duree: 45,
    dateLimite: "12 juil. 2026 à 23h59",
    statut: "disponible",
    noteStandard: 20,
    questions: [
      {
        id: 1,
        type: "QCM",
        enonce:
          "Quelle structure de données suit le principe LIFO (Last In, First Out) ?",
        points: 2,
        estChoixMultiple: false,
        choix: [
          { id: 1, texte: "File (Queue)", estCorrect: false },
          { id: 2, texte: "Pile (Stack)", estCorrect: true },
          { id: 3, texte: "Liste chaînée", estCorrect: false },
          { id: 4, texte: "Arbre binaire", estCorrect: false },
        ],
      },
      {
        id: 2,
        type: "QCM",
        enonce:
          "Parmi les propositions suivantes, lesquelles sont des structures de données linéaires ?",
        points: 3,
        estChoixMultiple: true,
        choix: [
          { id: 5, texte: "Tableau", estCorrect: true },
          { id: 6, texte: "Arbre binaire de recherche", estCorrect: false },
          { id: 7, texte: "Liste chaînée", estCorrect: true },
          { id: 8, texte: "File", estCorrect: true },
        ],
      },
      {
        id: 3,
        type: "QCM",
        enonce:
          "Quelle est la complexité temporelle moyenne de la recherche dans une table de hachage ?",
        points: 2,
        estChoixMultiple: false,
        choix: [
          { id: 9, texte: "O(n)", estCorrect: false },
          { id: 10, texte: "O(log n)", estCorrect: false },
          { id: 11, texte: "O(1)", estCorrect: true },
          { id: 12, texte: "O(n log n)", estCorrect: false },
        ],
      },
    ],
  },

  {
    id: 2,
    titre: "Algorithmique — exercice de code",
    description:
      "Résolvez les exercices dans le langage de votre choix. Votre code est exécuté automatiquement contre une série de cas de test.",
    type: "CODE",
    duree: 90,
    dateLimite: "15 juil. 2026 à 23h59",
    statut: "disponible",
    noteStandard: 20,
    questions: [
      {
        id: 30,
        type: "CODE",
        enonce:
          "Écrivez un programme qui lit un entier n sur l'entrée standard et affiche la somme des entiers de 1 à n.",
        points: 10,
        consigne:
          "L'entrée contient un seul entier. La sortie doit contenir uniquement le résultat, suivi d'un retour à la ligne.",
        langagesAutorises: ["c", "cpp", "java", "python", "javascript"],
        casDeTests: [
          { id: 300, entree: "5", sortieAttendue: "15", estMasque: false },
          { id: 301, entree: "10", sortieAttendue: "55", estMasque: false },
          { id: 302, entree: "100", sortieAttendue: "5050", estMasque: true },
          { id: 303, entree: "1", sortieAttendue: "1", estMasque: true },
        ],
      },
      {
        id: 31,
        type: "CODE",
        enonce:
          "Écrivez un programme qui lit une chaîne de caractères et affiche « OUI » si c'est un palindrome, « NON » sinon.",
        points: 10,
        consigne: "La comparaison est sensible à la casse. Sortie exacte attendue : OUI ou NON.",
        langagesAutorises: ["c", "cpp", "java", "python", "javascript"],
        casDeTests: [
          { id: 310, entree: "radar", sortieAttendue: "OUI", estMasque: false },
          { id: 311, entree: "bonjour", sortieAttendue: "NON", estMasque: false },
          { id: 312, entree: "a", sortieAttendue: "OUI", estMasque: true },
          { id: 313, entree: "abba", sortieAttendue: "OUI", estMasque: true },
        ],
      },
    ],
  },

  {
    id: 3,
    titre: "Réseaux — questions ouvertes",
    description:
      "Questions ouvertes sur les protocoles réseau, l'adressage IP et le routage.",
    type: "OUVERTE",
    duree: 60,
    dateLimite: "5 juil. 2026 à 23h59",
    statut: "terminee",
    noteStandard: 20,
    questions: [
      {
        id: 20,
        type: "QCM",
        enonce: "Quel protocole assure la livraison fiable des paquets ?",
        points: 4,
        estChoixMultiple: false,
        choix: [
          { id: 40, texte: "UDP", estCorrect: false },
          { id: 41, texte: "TCP", estCorrect: true },
          { id: 42, texte: "ICMP", estCorrect: false },
          { id: 43, texte: "ARP", estCorrect: false },
        ],
      },
      {
        id: 21,
        type: "QCM",
        enonce: "Parmi ces adresses, lesquelles sont des adresses privées ?",
        points: 3,
        estChoixMultiple: true,
        choix: [
          { id: 44, texte: "192.168.1.1", estCorrect: true },
          { id: 45, texte: "8.8.8.8", estCorrect: false },
          { id: 46, texte: "10.0.0.5", estCorrect: true },
          { id: 47, texte: "172.16.0.1", estCorrect: true },
        ],
      },
      {
        id: 22,
        type: "QCM",
        enonce: "À quelle couche du modèle OSI appartient le routage IP ?",
        points: 3,
        estChoixMultiple: false,
        choix: [
          { id: 48, texte: "Couche liaison (2)", estCorrect: false },
          { id: 49, texte: "Couche réseau (3)", estCorrect: true },
          { id: 50, texte: "Couche transport (4)", estCorrect: false },
          { id: 51, texte: "Couche application (7)", estCorrect: false },
        ],
      },
    ],
    resultat: {
      resultatsPublies: true,
      dateSoumission: "5 juil. 2026 à 14h32",
      noteFinale: 16, // (8 / 10) × 20
      pointsObtenus: 8,
      pointsMax: 10,
      moyennePromotion: 13.5,
      feedbackGeneral:
        "Bonne maîtrise des protocoles et de l'adressage. Attention aux plages d'adresses privées.",
      reponsesEtudiant: {
        20: [41], // correct
        21: [44, 46], // partiel : manque 172.16.0.1
        22: [49], // correct
      },
      commentaires: {
        21: "La plage 172.16.0.0/12 fait aussi partie des adresses privées définies par la RFC 1918.",
      },
    },
  },

  {
    id: 4,
    titre: "Bases de données — quiz chronométré",
    description:
      "Quiz sur le modèle relationnel, SQL et la normalisation. Chaque question a une durée limitée.",
    type: "QUIZ",
    duree: 30,
    dateLimite: "10 juil. 2026 à 18h00",
    statut: "disponible",
    noteStandard: 20,
    questions: [],
  },

  {
    id: 5,
    titre: "Génie logiciel — questions ouvertes",
    description:
      "Répondez de manière argumentée aux questions portant sur les méthodes de conception et les bonnes pratiques de développement.",
    type: "OUVERTE",
    duree: 60,
    dateLimite: "20 juil. 2026 à 23h59",
    statut: "disponible",
    noteStandard: 20,
    questions: [
      {
        id: 60,
        type: "OUVERTE",
        enonce:
          "Expliquez la différence entre une relation d'agrégation et une relation de composition en UML. Illustrez par un exemple concret.",
        points: 5,
        consigne:
          "Attendu : définition des deux notions + un exemple pour chacune.",
      },
      {
        id: 61,
        type: "OUVERTE",
        enonce:
          "Quels sont les avantages d'une architecture client-serveur découplée par rapport à une application monolithique ?",
        points: 5,
        consigne: "Citez au moins trois avantages et justifiez-les.",
      },
      {
        id: 62,
        type: "OUVERTE",
        enonce:
          "Dans un projet à plusieurs développeurs, pourquoi utilise-t-on des branches Git plutôt que de travailler directement sur la branche principale ?",
        points: 5,
        consigne: "Argumentez en vous appuyant sur des situations concrètes.",
      },
    ],
  },
]

// ---------- Helpers ----------
export function getEvaluation(id: number | string | undefined) {
  if (id === undefined) return undefined
  return evaluationsMock.find((e) => e.id === Number(id))
}

export function getTotalPoints(evaluation: EvaluationMock) {
  return evaluation.questions.reduce((somme, q) => somme + q.points, 0)
}

// ============================================================
// Données de performance de l'étudiant
// Reflète TableauDeBordEtudiant du diagramme de classes
// ============================================================

export interface PointHistorique {
  evaluation: string // libellé court pour l'axe X du graphique
  titreComplet: string
  date: string
  note: number
  moyennePromotion: number
  noteStandard: number
}

export interface PerformanceEtudiant {
  moyenneGenerale: number
  tauxReussite: number
  rang: number
  effectifPromotion: number
  moyennePromotionGenerale: number
  historique: PointHistorique[]
}

// Données mockées — à remplacer par GET /api/etudiants/{id}/performances
export const performanceMock: PerformanceEtudiant = {
  moyenneGenerale: 14.2,
  tauxReussite: 80,
  rang: 7,
  effectifPromotion: 42,
  moyennePromotionGenerale: 12.8,
  historique: [
    {
      evaluation: "Algo",
      titreComplet: "Algorithmique — QCM",
      date: "12 mars 2026",
      note: 12,
      moyennePromotion: 11.5,
      noteStandard: 20,
    },
    {
      evaluation: "Prog C",
      titreComplet: "Programmation C — exercice de code",
      date: "28 mars 2026",
      note: 9,
      moyennePromotion: 10.2,
      noteStandard: 20,
    },
    {
      evaluation: "SysExp",
      titreComplet: "Systèmes d'exploitation — QCM",
      date: "15 avr. 2026",
      note: 15,
      moyennePromotion: 12.9,
      noteStandard: 20,
    },
    {
      evaluation: "BDD",
      titreComplet: "Bases de données — quiz",
      date: "3 mai 2026",
      note: 17,
      moyennePromotion: 13.4,
      noteStandard: 20,
    },
    {
      evaluation: "Réseaux",
      titreComplet: "Réseaux — questions ouvertes",
      date: "5 juil. 2026",
      note: 16,
      moyennePromotion: 13.5,
      noteStandard: 20,
    },
  ],
}