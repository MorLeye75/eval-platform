import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/context/AuthContext"
import { ClipboardList, Award, CircleCheckBig, ArrowRight, PlayCircle } from "lucide-react"
import { messageErreur } from "@/lib/passation"
import {
  CLE_MES_EVALUATIONS,
  etatEvaluation,
  formatDateHeure,
  mesEvaluations,
  type EtatEvaluation,
} from "@/lib/evaluationsEtudiant"
import { CLE_PERFORMANCES, formatNote, mesPerformances } from "@/lib/resultats"
import type { EvaluationEtudiant } from "@/types"

/** Nombre d'évaluations affichées sur l'accueil (la liste complète est dans « Mes évaluations »). */
const NB_RECENTES = 4

/** Disponibles d'abord (à passer maintenant), puis à venir, puis terminées ; ordre du serveur conservé sinon. */
const PRIORITE: Record<EtatEvaluation, number> = { disponible: 0, a_venir: 1, terminee: 2 }

const STYLES_BADGE = {
  success: "bg-primary/10 text-primary border-primary/20",
  warning: "bg-amber-500/10 text-amber-700 border-amber-500/20 dark:text-amber-400",
  muted: "bg-muted text-muted-foreground border-transparent",
}

interface LigneEvaluation {
  evaluation: EvaluationEtudiant
  cible: string
  detail: string
  statut: string
  variante: keyof typeof STYLES_BADGE
}

function ligne(e: EvaluationEtudiant, maintenant: Date): LigneEvaluation {
  const etat = etatEvaluation(e, maintenant)
  const detailEvaluation = `/etudiant/evaluations/${e.id}`
  if (etat === "disponible") {
    const enCours = e.statutSoumission === "EN_COURS"
    return {
      evaluation: e,
      cible: detailEvaluation,
      detail: `Durée ${e.dureeMinutes} min · jusqu'au ${formatDateHeure(e.dateFermeture)}`,
      statut: enCours ? "Copie en cours" : "Disponible",
      variante: "success",
    }
  }
  if (etat === "a_venir") {
    return {
      evaluation: e,
      cible: detailEvaluation,
      detail: `Ouvre le ${formatDateHeure(e.dateOuverture)}`,
      statut: "À venir",
      variante: "warning",
    }
  }
  // Terminée : la note n'est envoyée par le serveur qu'une fois les résultats publiés
  if (e.noteFinale !== null && e.soumissionId !== null) {
    return {
      evaluation: e,
      cible: `/etudiant/resultats/${e.soumissionId}`,
      detail: `Note : ${formatNote(e.noteFinale)} / ${formatNote(e.totalPoints)}`,
      statut: "Résultats publiés",
      variante: "muted",
    }
  }
  return {
    evaluation: e,
    cible: detailEvaluation,
    detail: e.statutSoumission ? "Copie remise · en attente des résultats" : "Non passée",
    statut: "Terminée",
    variante: "muted",
  }
}

export default function DashboardEtudiant() {
  const { user } = useAuth()
  const evaluations = useQuery({ queryKey: CLE_MES_EVALUATIONS, queryFn: mesEvaluations })
  const performances = useQuery({ queryKey: CLE_PERFORMANCES, queryFn: mesPerformances })

  const maintenant = new Date()
  const liste = evaluations.data ?? []
  const disponibles = liste.filter((e) => etatEvaluation(e, maintenant) === "disponible").length
  const recentes = liste
    .map((e, rang) => ({ e, rang, priorite: PRIORITE[etatEvaluation(e, maintenant)] }))
    .sort((a, b) => a.priorite - b.priorite || a.rang - b.rang)
    .slice(0, NB_RECENTES)
    .map(({ e }) => ligne(e, maintenant))

  // Indicateurs sur les seuls résultats publiés ; « — » tant qu'il n'y en a pas (ou si la requête échoue)
  const p = performances.data
  const reussies = p ? p.historique.filter((h) => h.noteSur20 >= 10).length : 0
  const stats = [
    {
      label: "Disponibles maintenant",
      valeur: evaluations.data ? String(disponibles) : "—",
      icon: PlayCircle,
    },
    {
      label: "Moyenne générale",
      valeur: p?.moyenneGenerale != null ? `${formatNote(p.moyenneGenerale)} / 20` : "—",
      icon: Award,
    },
    {
      label: "Taux de réussite",
      valeur: p && p.nbEvaluations > 0 ? `${Math.round((reussies / p.nbEvaluations) * 100)} %` : "—",
      icon: CircleCheckBig,
    },
  ]

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-7">
      {/* En-tête */}
      <div>
        <h1 className="flex items-center gap-2 text-3xl font-extrabold tracking-tight">
          Bonjour{user?.prenom ? `, ${user.prenom}` : ""} <span className="text-2xl">👋</span>
        </h1>
        <p className="mt-1 text-muted-foreground">Voici un aperçu rapide de vos évaluations et de vos résultats.</p>
      </div>

      {/* Indicateurs */}
      <div className="grid gap-4 md:grid-cols-3">
        {stats.map((stat) => (
          <Card key={stat.label} className="rounded-2xl border-border/60 shadow-sm transition-shadow hover:shadow-md">
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">{stat.label}</CardTitle>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <stat.icon className="size-4" />
              </span>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-extrabold tracking-tight">{stat.valeur}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Évaluations récentes */}
      <Card className="overflow-hidden rounded-2xl border-border/60 shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base font-bold">Mes évaluations récentes</CardTitle>
          <Button
            variant="ghost"
            size="sm"
            render={
              <Link to="/etudiant/evaluations">
                Tout voir
                <ArrowRight className="ml-1 size-4" />
              </Link>
            }
          />
        </CardHeader>
        <CardContent className="flex flex-col gap-0 p-0">
          {evaluations.isPending ? (
            <p className="px-6 py-6 text-sm text-muted-foreground">Chargement…</p>
          ) : evaluations.error ? (
            <p className="px-6 py-6 text-sm text-muted-foreground">
              {messageErreur(evaluations.error, "Impossible de charger vos évaluations.")}
            </p>
          ) : recentes.length === 0 ? (
            <p className="px-6 py-6 text-sm text-muted-foreground">Aucune évaluation pour votre promotion pour le moment.</p>
          ) : (
            recentes.map(({ evaluation, cible, detail, statut, variante }, index) => (
              <Link
                key={evaluation.id}
                to={cible}
                className={`flex items-center justify-between gap-4 px-6 py-4 transition-colors hover:bg-muted/40 ${
                  index !== recentes.length - 1 ? "border-b border-border/60" : ""
                }`}
              >
                <div className="flex min-w-0 items-center gap-3">
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-muted">
                    <ClipboardList className="size-4 text-muted-foreground" />
                  </div>
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{evaluation.titre}</p>
                    <p className="text-sm text-muted-foreground">{detail}</p>
                  </div>
                </div>
                <Badge
                  variant="outline"
                  className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${STYLES_BADGE[variante]}`}
                >
                  {statut}
                </Badge>
              </Link>
            ))
          )}
        </CardContent>
      </Card>

      {/* Accès au tableau de bord de performances */}
      <Card className="rounded-2xl border-primary/15 bg-gradient-to-br from-primary/[0.06] to-primary/[0.02] shadow-sm">
        <CardContent className="flex flex-col items-start justify-between gap-4 py-6 sm:flex-row sm:items-center">
          <div className="flex items-center gap-4">
            <div className="flex size-12 items-center justify-center rounded-2xl bg-primary/10">
              <Award className="size-6 text-primary" />
            </div>
            <div>
              <p className="font-bold">Suivi de vos performances</p>
              <p className="text-sm text-muted-foreground">
                Évolution dans le temps et comparaison avec la moyenne de la promotion.
              </p>
            </div>
          </div>
          <Button
            className="shadow-md"
            render={
              <Link to="/etudiant/performances">
                Voir mes performances détaillées
                <ArrowRight className="ml-1 size-4" />
              </Link>
            }
          />
        </CardContent>
      </Card>
    </div>
  )
}
