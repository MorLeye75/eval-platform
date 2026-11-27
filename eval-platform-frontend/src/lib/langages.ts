/** Langages proposés dans l'éditeur de code (identifiant Monaco -> libellé), acceptés par le serveur. */
export const LANGAGES_LABELS: Record<string, string> = {
  c: "C",
  cpp: "C++",
  java: "Java",
  python: "Python",
  javascript: "JavaScript",
}

export const LANGAGES = Object.keys(LANGAGES_LABELS)

export const LANGAGE_PAR_DEFAUT = LANGAGES[0]
