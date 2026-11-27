import { useQuery } from "@tanstack/react-query"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { CLE_CONFIGURATION, configurationSysteme } from "@/lib/adminVue"
import { messageErreur } from "@/lib/passation"

// ============================================================
// Configuration en vigueur (DS-18, section D) : ce que le serveur applique réellement, en lecture seule. Chaque valeur
// vient du serveur et dit d'où elle vient. Aucun secret n'est exposé par l'API.
// ============================================================

const LIBELLE_SOURCE: Record<string, string> = {
  CODE: "Fixé dans le code",
  DEPLOIEMENT: "Configuration du déploiement",
}

export default function ParametresSysteme() {
  const configuration = useQuery({ queryKey: CLE_CONFIGURATION, queryFn: configurationSysteme })

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Paramètres système</h1>
        <p className="mt-1 text-muted-foreground">Configuration réellement appliquée par la plateforme, en lecture seule.</p>
      </div>

      <p className="rounded-xl bg-muted px-4 py-2.5 text-sm text-muted-foreground">
        Les valeurs « configuration du déploiement » se changent dans la configuration du serveur (variables d'environnement) ;
        celles « fixées dans le code » demandent une nouvelle version de l'application. Cette page ne modifie rien.
      </p>

      {configuration.isPending && <p role="status" className="py-8 text-center text-sm text-muted-foreground">Chargement de la configuration…</p>}
      {configuration.isError && (
        <p role="alert" className="py-8 text-center text-sm text-destructive">
          {messageErreur(configuration.error, "Impossible de charger la configuration.")}{" "}
          <button type="button" onClick={() => configuration.refetch()} className="underline">Réessayer</button>
        </p>
      )}

      {configuration.isSuccess && (
        <div className="grid gap-4 md:grid-cols-2">
          {configuration.data.sections.map((section) => (
            <Card key={section.titre} className="rounded-2xl border-border/60 shadow-sm">
              <CardHeader className="pb-2">
                <CardTitle className="text-base font-bold">{section.titre}</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="flex flex-col divide-y divide-border">
                  {section.parametres.map((p) => (
                    <div key={p.libelle} className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0">
                      <dt className="text-sm text-muted-foreground">{p.libelle}</dt>
                      <dd className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-sm font-semibold">{p.valeur}</span>
                        <Badge variant="outline" className="rounded-full text-[11px] font-medium">
                          {LIBELLE_SOURCE[p.source] ?? p.source}
                        </Badge>
                      </dd>
                    </div>
                  ))}
                </dl>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
