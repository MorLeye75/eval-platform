import { useState, useEffect, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useExamTimer } from "@/hooks/useExamTimer"
import { useAntiCheat } from "@/hooks/useAntiCheat"
import { useAutoSave, lireTampon, viderTampon } from "@/hooks/useAutoSave"
import {
  demarrerEpreuve,
  sauvegarderReponses,
  soumettreCopie,
  signalerChangementOnglet,
  estCopieVerrouillee,
  messageErreur,
} from "@/lib/passation"
import { cleTamponCopie } from "@/lib/session"
import type { CopieEnCours, QuestionExamen, ReponseItem } from "@/types"

interface UsePassationOptions<T> {
  evaluationId: string | undefined
  /** Réponses du formulaire reconstruites depuis la copie enregistrée sur le serveur. */
  depuisCopie: (questions: QuestionExamen[]) => T
  /** Réponses du formulaire converties pour PUT /soumissions/{id}/sauvegarder. */
  versReponses: (reponses: T) => ReponseItem[]
  /** Nombre de questions traitées (affiché sur l'écran de fin). */
  compterTraitees: (reponses: T) => number
}

type Fin = "confirmation" | "expiration"

/**
 * Passation d'une épreuve côté étudiant :
 * - démarre ou reprend la copie (questions, réponses déjà enregistrées, temps restant serveur) ;
 * - sauvegarde automatique toutes les 30 s (DS-02) ;
 * - soumission finale (DS-01), aussi déclenchée par la fin du chrono.
 */
export function usePassation<T>({
  evaluationId,
  depuisCopie,
  versReponses,
  compterTraitees,
}: UsePassationOptions<T>) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const {
    data: copie,
    isPending,
    error,
    refetch,
  } = useQuery({
    queryKey: ["passation", evaluationId],
    queryFn: () => demarrerEpreuve(evaluationId!),
    enabled: !!evaluationId,
    // Démarrer crée la copie : jamais de relance automatique, et rechargée à chaque visite
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  })

  const [reponses, setReponses] = useState<T>(() => depuisCopie([]))
  const [pret, setPret] = useState(false)
  const [soumissionEnCours, setSoumissionEnCours] = useState(false)
  const [erreurSoumission, setErreurSoumission] = useState<string | null>(null)
  const [avertissement, setAvertissement] = useState<string | null>(null)
  const termine = useRef(false)
  // Copie dont les réponses ont été chargées dans le formulaire (rechargée après un conflit de version)
  const copieInitialisee = useRef<typeof copie>(undefined)

  const cleTampon = copie ? cleTamponCopie(copie.id) : undefined
  const enCours = copie?.statut === "EN_COURS"

  const versReponsesRef = useRef(versReponses)
  const compterTraiteesRef = useRef(compterTraitees)
  const reponsesRef = useRef(reponses)
  useEffect(() => {
    versReponsesRef.current = versReponses
    compterTraiteesRef.current = compterTraitees
    reponsesRef.current = reponses
  })

  function quitter(fin: Fin, dateRemise: string | null = null) {
    if (termine.current) return
    termine.current = true
    if (cleTampon) viderTampon(cleTampon)
    navigate(`/examen/${evaluationId}/${fin}`, {
      replace: true,
      state: {
        titre: copie?.titreEvaluation,
        // Quiz : le navigateur ne connaît que la question en cours, pas les réponses aux questions passées
        ...(copie?.quiz
          ? {}
          : { traitees: compterTraiteesRef.current(reponsesRef.current), total: copie?.questions.length ?? 0 }),
        // Heure de remise du serveur (affichée à l'étudiant), pas celle du navigateur
        dateRemise,
      },
    })
  }

  const { derniereSauvegarde, erreurSauvegarde, sauvegarderMaintenant, marquerSauvegarde } =
    useAutoSave({
      data: reponses,
      onSave: async (donnees, versionAttendue) => {
        const r = await sauvegarderReponses(copie!.id, versReponsesRef.current(donnees), versionAttendue)
        return { dateSauvegarde: r.dateSauvegarde, version: r.versionReponses }
      },
      cleTampon,
      // DS-02 cas d'erreur : temps expiré ou copie verrouillée → l'édition est bloquée
      onVerrouillage: () => quitter("expiration"),
      // DS-02 conflit de version : la copie enregistrée ailleurs est rechargée, les réponses locales gardées de côté
      onConflit: () => {
        setAvertissement(
          "Votre copie a été modifiée depuis un autre onglet ou appareil : la version enregistrée a été rechargée."
        )
        refetch()
      },
      enabled: pret && enCours,
    })

  // Réponses de départ : celles du serveur (source de vérité), ou le tampon local s'il reste des réponses
  // non confirmées saisies sur cette même version (coupure réseau puis rechargement de la page)
  useEffect(() => {
    if (!copie || copieInitialisee.current === copie) return
    copieInitialisee.current = copie
    const cle = cleTamponCopie(copie.id)
    // Copie remise : plus rien à synchroniser
    if (!enCours) viderTampon(cle)
    const serveur = depuisCopie(copie.questions)
    const tampon = enCours ? lireTampon<T>(cle, copie.versionReponses) : null
    setReponses(tampon ?? serveur)
    marquerSauvegarde(serveur, copie.versionReponses)
    setPret(true)
  }, [copie, enCours, depuisCopie, marquerSauvegarde])

  /** DS-01 : soumission de la copie (bouton « Soumettre » ou fin du chrono). */
  async function soumettre(fin: Fin = "confirmation") {
    if (!copie || termine.current || soumissionEnCours) return
    setSoumissionEnCours(true)
    setErreurSoumission(null)

    // Les modifications des dernières secondes partent avant que la copie soit figée
    const sauvegarde = await sauvegarderMaintenant()
    if (sauvegarde === "verrouillee") return
    if (sauvegarde === "conflit" && fin === "confirmation") {
      // La copie vient d'être rechargée : l'étudiant vérifie ses réponses avant de soumettre
      setErreurSoumission(
        "Votre copie a été modifiée ailleurs et vient d'être rechargée. Vérifiez vos réponses puis soumettez à nouveau."
      )
      setSoumissionEnCours(false)
      return
    }
    if (sauvegarde === "echec" && fin === "confirmation") {
      // Soumettre maintenant figerait la copie sans les dernières réponses, restées dans le tampon local
      setErreurSoumission(
        "Vos dernières réponses ne sont pas encore enregistrées. Vérifiez votre connexion puis réessayez."
      )
      setSoumissionEnCours(false)
      return
    }
    // Fin du chrono : soumission quoi qu'il arrive, seules les réponses déjà enregistrées sur le serveur sont garanties

    try {
      const dateRemise = await soumettreCopie(copie.id)
      quitter(fin, dateRemise)
    } catch (err) {
      if (estCopieVerrouillee(err)) {
        // DS-01 cas critique : temps dépassé au-delà de la marge, copie figée par le serveur
        quitter("expiration")
      } else if (fin === "expiration") {
        // Chrono terminé sans réseau : le serveur clôturera la copie à l'état de la dernière sauvegarde
        quitter("expiration")
      } else {
        setErreurSoumission(
          messageErreur(err, "La soumission a échoué. Vérifiez votre connexion et réessayez.")
        )
        setSoumissionEnCours(false)
      }
    }
  }

  const { tempsRestant, tempsRestantFormate } = useExamTimer({
    dureeSecondes: copie?.tempsRestantSecondes ?? 0,
    onExpire: () => soumettre("expiration"),
    enabled: pret && enCours,
  })

  // Anti-triche : compteur repris du serveur, chaque nouvelle sortie lui est signalée (il fait foi)
  const { changementsOnglet, setChangementsOnglet } = useAntiCheat({
    initialCount: copie?.nbChangementsOnglet ?? 0,
    enabled: pret && enCours && !soumissionEnCours,
    onTabSwitch: () => {
      if (!copie) return
      signalerChangementOnglet(copie.id)
        .then(setChangementsOnglet)
        .catch(() => {
          // réseau absent : la sortie reste affichée localement, le serveur ne l'aura pas comptée
        })
    },
  })

  let erreur: string | null = null
  if (!evaluationId) erreur = "Évaluation introuvable."
  else if (error) erreur = messageErreur(error, "Impossible de charger votre copie.")
  else if (copie && !enCours) erreur = "Votre copie a déjà été remise."

  return {
    copie,
    questions: copie?.questions ?? [],
    chargement: !!evaluationId && (isPending || (!!copie && enCours && !pret)),
    erreur,
    reponses,
    setReponses,
    tempsRestant,
    tempsRestantFormate,
    derniereSauvegarde,
    erreurSauvegarde,
    soumettre,
    soumissionEnCours,
    erreurSoumission,
    avertissement,
    changementsOnglet,
    sauvegarderMaintenant,
    /** Remplace la copie par celle renvoyée par le serveur (quiz : question suivante). */
    remplacerCopie: (nouvelle: CopieEnCours) => queryClient.setQueryData(["passation", evaluationId], nouvelle),
  }
}
