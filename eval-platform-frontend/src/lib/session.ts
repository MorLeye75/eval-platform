/**
 * Données de session conservées dans le navigateur, et leur nettoyage (poste partagé : salle de TP, bibliothèque).
 */

/** Brouillon de l'assistant de création (sessionStorage) : contient les bonnes réponses et sorties attendues. */
export const CLE_BROUILLON_EVALUATION = "brouillon-evaluation"

const PREFIXE_TAMPON_COPIE = "passation-copie-"

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
  retirer(localStorage, "token")
  retirer(localStorage, "user")
  retirer(sessionStorage, CLE_BROUILLON_EVALUATION)
  if (garderTamponsCopies) return
  try {
    const cles = Object.keys(localStorage).filter((cle) => cle.startsWith(PREFIXE_TAMPON_COPIE))
    cles.forEach((cle) => retirer(localStorage, cle))
  } catch {
    // stockage indisponible : rien à retirer
  }
}
