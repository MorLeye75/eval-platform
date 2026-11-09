/**
 * Données de session conservées dans le navigateur, et leur nettoyage (poste partagé : salle de TP, bibliothèque).
 */

/** Brouillon de l'assistant de création (sessionStorage) : contient les bonnes réponses et sorties attendues. */
export const CLE_BROUILLON_EVALUATION = "brouillon-evaluation"

const PREFIXE_TAMPON_COPIE = "passation-copie-"

/** Email du compte à qui appartiennent les réponses locales gardées après un jeton refusé. */
const CLE_PROPRIETAIRE_TAMPONS = "proprietaire-tampons"

const normaliser = (email: string) => email.trim().toLowerCase()

/** Email de l'utilisateur enregistré dans le navigateur, s'il y en a un. */
function emailEnregistre(): string | null {
  try {
    const utilisateur = JSON.parse(localStorage.getItem("user") ?? "null") as { email?: string } | null
    return utilisateur?.email ? normaliser(utilisateur.email) : null
  } catch {
    return null
  }
}

/** Clé localStorage des réponses d'une copie pas encore confirmées par le serveur (DS-02). */
export function cleTamponCopie(soumissionId: number) {
  return `${PREFIXE_TAMPON_COPIE}${soumissionId}`
}

function retirer(stockage: Storage, cle: string) {
  try {
    stockage.removeItem(cle)
  } catch {
    // stockage indisponible (navigation privée) : rien à retirer
  }
}

/**
 * Efface ce que la session a laissé dans le navigateur : jeton, utilisateur, brouillon d'évaluation et,
 * sauf demande contraire, les sauvegardes locales des copies (réponses d'un étudiant).
 *
 * `garderTamponsCopies` : jeton expiré pendant une coupure, par exemple. Les réponses non envoyées
 * sont gardées pour l'étudiant qui se reconnecte ; elles ne s'appliquent qu'à sa propre copie.
 */
export function viderSession({ garderTamponsCopies = false } = {}) {
  const proprietaire = emailEnregistre()
  retirer(localStorage, "token")
  retirer(localStorage, "user")
  retirer(sessionStorage, CLE_BROUILLON_EVALUATION)
  if (garderTamponsCopies) {
    // À la prochaine connexion, ces réponses ne sont gardées que si c'est le même compte
    try {
      if (proprietaire) localStorage.setItem(CLE_PROPRIETAIRE_TAMPONS, proprietaire)
    } catch {
      // stockage indisponible
    }
    return
  }
  retirer(localStorage, CLE_PROPRIETAIRE_TAMPONS)
  try {
    const cles = Object.keys(localStorage).filter((cle) => cle.startsWith(PREFIXE_TAMPON_COPIE))
    cles.forEach((cle) => retirer(localStorage, cle))
  } catch {
    // stockage indisponible : rien à retirer
  }
}

/**
 * Avant d'ouvrir la session de `email` : si le navigateur garde encore les données d'un autre compte (pas de
 * déconnexion, ou jeton expiré), elles sont effacées. Le même compte retrouve ses réponses non envoyées.
 */
export function preparerSession(email: string) {
  let precedent = emailEnregistre()
  try {
    precedent = precedent ?? localStorage.getItem(CLE_PROPRIETAIRE_TAMPONS)
  } catch {
    // stockage indisponible
  }
  if (precedent && precedent !== normaliser(email)) viderSession()
  retirer(localStorage, CLE_PROPRIETAIRE_TAMPONS)
}
