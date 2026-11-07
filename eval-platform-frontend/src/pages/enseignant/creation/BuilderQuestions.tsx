import { useState, type SetStateAction } from "react"
import { Navigate, useNavigate } from "react-router-dom"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Plus,
  Trash2,
  ChevronRight,
  ChevronUp,
  ChevronDown,
  CheckSquare,
  Code2,
  AlignLeft,
  Timer,
  GripVertical,
} from "lucide-react"
import { CreationStepper } from "./InfosGenerales"
import {
  useBrouillon,
  validerInfos,
  erreurQuestion,
  basculerChoixMultiple,
  nouvelId as newId,
  type ChoixLocal,
  type CasTestLocal,
  type QuestionLocal,
} from "./brouillon"
import type { TypeQuestion } from "@/types"

// ============================================================
// Étape 2 / 3 — Builder de questions
// Les questions sont conservées dans le brouillon (voir brouillon.ts).
// TODO : toutes les données sont envoyées en une fois lors de
// la prévisualisation → POST /api/enseignant/evaluations
// ============================================================

const TYPE_CONFIG: Record<TypeQuestion, { label: string; icon: React.ElementType; cls: string }> = {
  QCM:              { label: "QCM",              icon: CheckSquare, cls: "bg-primary/10 text-primary border-primary/20"                        },
  CODE:             { label: "Code",             icon: Code2,       cls: "bg-blue-500/10 text-blue-700 border-blue-500/20 dark:text-blue-400"  },
  OUVERTE:          { label: "Question ouverte", icon: AlignLeft,   cls: "bg-yellow-500/10 text-yellow-700 border-yellow-500/20"               },
  QUIZ_CHRONOMETREE:{ label: "Quiz chronométré", icon: Timer,       cls: "bg-purple-500/10 text-purple-700 border-purple-500/20"               },
}

function deuxChoixVides(): ChoixLocal[] {
  return [{ id: newId(), texte: "", estCorrect: false }, { id: newId(), texte: "", estCorrect: false }]
}

function emptyQuestion(type: TypeQuestion): QuestionLocal {
  const base = { localId: newId(), type, enonce: "", points: 2 }
  if (type === "QCM")               return { ...base, estChoixMultiple: false, choix: deuxChoixVides() }
  if (type === "CODE")              return { ...base, casTests: [{ id: newId(), entree: "", sortieAttendue: "", estMasque: false }] }
  // Le quiz se répond en cochant des choix (PassationQuiz), comme un QCM
  if (type === "QUIZ_CHRONOMETREE") return { ...base, dureeQuestion: 30, estChoixMultiple: false, choix: deuxChoixVides() }
  return base // OUVERTE
}

// ──────────────────────────────────────────────────────────
//  Composant principal
// ──────────────────────────────────────────────────────────

export default function BuilderQuestions() {
  const navigate  = useNavigate()
  const { brouillon, setBrouillon, estEdition, chemin } = useBrouillon()
  const { infos, questions } = brouillon

  const [typeAjout, setTypeAjout] = useState<TypeQuestion>("QCM")
  const [expanded,  setExpanded]  = useState<number | null>(null)
  const [tentative, setTentative] = useState(false)  // erreurs affichées après un essai de passage

  function setQuestions(action: SetStateAction<QuestionLocal[]>) {
    setBrouillon((b) => ({
      ...b,
      questions: typeof action === "function" ? action(b.questions) : action,
    }))
  }

  // ── helpers ─────────────────────────────────────────────

  function addQuestion() {
    const q = emptyQuestion(typeAjout)
    setQuestions((qs) => [...qs, q])
    setExpanded(q.localId)
  }

  function removeQuestion(id: number) {
    setQuestions((qs) => qs.filter((q) => q.localId !== id))
    if (expanded === id) setExpanded(null)
  }

  function updateQuestion(id: number, patch: Partial<QuestionLocal>) {
    setQuestions((qs) => qs.map((q) => q.localId === id ? { ...q, ...patch } : q))
  }

  function moveUp(idx: number) {
    if (idx === 0) return
    setQuestions((qs) => { const a = [...qs]; [a[idx-1], a[idx]] = [a[idx], a[idx-1]]; return a })
  }

  function moveDown(idx: number) {
    setQuestions((qs) => { if (idx === qs.length - 1) return qs; const a = [...qs]; [a[idx], a[idx+1]] = [a[idx+1], a[idx]]; return a })
  }

  // ── choix QCM ──────────────────────────────────────────

  function addChoix(qId: number) {
    updateQuestion(qId, {
      choix: [...(questions.find(q => q.localId === qId)?.choix ?? []), { id: newId(), texte: "", estCorrect: false }]
    })
  }

  function updateChoix(qId: number, cId: number, patch: Partial<ChoixLocal>) {
    setQuestions(qs => qs.map(q => q.localId !== qId ? q : {
      ...q,
      choix: q.choix?.map(c => c.id === cId ? { ...c, ...patch } : c)
    }))
  }

  function removeChoix(qId: number, cId: number) {
    setQuestions(qs => qs.map(q => q.localId !== qId ? q : {
      ...q,
      choix: q.choix?.filter(c => c.id !== cId)
    }))
  }

  // ── cas de test CODE ───────────────────────────────────

  function addCasTest(qId: number) {
    setQuestions(qs => qs.map(q => q.localId !== qId ? q : {
      ...q,
      casTests: [...(q.casTests ?? []), { id: newId(), entree: "", sortieAttendue: "", estMasque: false }]
    }))
  }

  function updateCasTest(qId: number, ctId: number, patch: Partial<CasTestLocal>) {
    setQuestions(qs => qs.map(q => q.localId !== qId ? q : {
      ...q,
      casTests: q.casTests?.map(ct => ct.id === ctId ? { ...ct, ...patch } : ct)
    }))
  }

  function removeCasTest(qId: number, ctId: number) {
    setQuestions(qs => qs.map(q => q.localId !== qId ? q : {
      ...q,
      casTests: q.casTests?.filter(ct => ct.id !== ctId)
    }))
  }

  // ── navigation ─────────────────────────────────────────

  function handleNext() {
    if (questions.length === 0) return
    const premiereInvalide = questions.find((q) => erreurQuestion(q) !== null)
    if (premiereInvalide) {
      setTentative(true)
      setExpanded(premiereInvalide.localId)
      return
    }
    navigate(chemin("previsualisation"))
  }

  // Étape 1 incomplète (accès direct à l'URL) → retour à l'étape 1
  if (Object.keys(validerInfos(infos)).length > 0) {
    return <Navigate to={chemin("infos")} replace />
  }

  const totalPoints = questions.reduce((s, q) => s + q.points, 0)
  const nbInvalides = questions.filter((q) => erreurQuestion(q) !== null).length

  // ──────────────────────────────────────────────────────
  //  Rendu
  // ──────────────────────────────────────────────────────

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">

      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">
          {estEdition ? "Modifier l'évaluation" : "Nouvelle évaluation"}
        </h1>
        <p className="mt-1 text-muted-foreground">
          {infos.titre} · {infos.duree} min
        </p>
      </div>

      <CreationStepper step={2} />

      {/* Barre d'ajout */}
      <Card className="rounded-2xl border-border/60 shadow-sm">
        <CardContent className="flex flex-wrap items-center gap-3 p-4">
          <Select value={typeAjout} onValueChange={(v) => setTypeAjout(v as TypeQuestion)}>
            <SelectTrigger className="w-52 rounded-xl">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="QCM">QCM</SelectItem>
              <SelectItem value="OUVERTE">Question ouverte</SelectItem>
              <SelectItem value="CODE">Exercice de code</SelectItem>
              <SelectItem value="QUIZ_CHRONOMETREE">Quiz chronométré</SelectItem>
            </SelectContent>
          </Select>
          <Button className="rounded-xl" onClick={addQuestion}>
            <Plus className="size-4" /> Ajouter une question
          </Button>
          {questions.length > 0 && (
            <span className="ml-auto text-sm text-muted-foreground">
              {questions.length} question{questions.length > 1 ? "s" : ""} · {totalPoints} pts
            </span>
          )}
        </CardContent>
      </Card>

      {/* Liste des questions */}
      {questions.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border py-16 text-center text-muted-foreground">
          <p className="font-medium">Aucune question ajoutée</p>
          <p className="text-sm">Choisissez un type et cliquez sur "Ajouter une question".</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {questions.map((q, idx) => {
            const cfg = TYPE_CONFIG[q.type]
            const isOpen = expanded === q.localId
            const erreur = tentative ? erreurQuestion(q) : null
            return (
              <Card key={q.localId} className="rounded-2xl border-border/60 shadow-sm">
                {/* En-tête question */}
                <div className="flex items-center gap-3 p-4">
                  <GripVertical className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                  {/* Titre de la question : bouton qui la déplie (les actions restent à côté, hors du bouton) */}
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    onClick={() => setExpanded(isOpen ? null : q.localId)}
                    className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 text-left"
                  >
                    <span className="text-sm font-bold text-muted-foreground">Q{idx + 1}</span>
                    <Badge variant="outline" className={`shrink-0 rounded-full text-[11px] font-semibold ${cfg.cls}`}>
                      {cfg.label}
                    </Badge>
                    <span className="truncate text-sm font-medium">
                      {q.enonce || <span className="text-muted-foreground italic">Sans énoncé</span>}
                    </span>
                    {erreur && (
                      <span className="shrink-0 text-[11px] font-medium text-destructive">{erreur}</span>
                    )}
                  </button>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className="text-sm font-bold">{q.points} pts</span>
                    <button type="button" aria-label={`Monter la question ${idx + 1}`} onClick={() => moveUp(idx)} className="p-1 text-muted-foreground hover:text-foreground"><ChevronUp   className="size-4" /></button>
                    <button type="button" aria-label={`Descendre la question ${idx + 1}`} onClick={() => moveDown(idx)} className="p-1 text-muted-foreground hover:text-foreground"><ChevronDown className="size-4" /></button>
                    <button type="button" aria-label={`Supprimer la question ${idx + 1}`} onClick={() => removeQuestion(q.localId)} className="p-1 text-muted-foreground hover:text-destructive"><Trash2 className="size-4" /></button>
                  </div>
                </div>

                {/* Corps (déplié) */}
                {isOpen && (
                  <CardContent className="flex flex-col gap-4 border-t border-border pt-4">

                    {/* Énoncé + points */}
                    <div className="grid gap-4 md:grid-cols-[1fr_120px]">
                      <div className="flex flex-col gap-1.5">
                        <Label>Énoncé</Label>
                        <textarea
                          value={q.enonce}
                          onChange={(e) => updateQuestion(q.localId, { enonce: e.target.value })}
                          rows={3}
                          className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none focus:border-primary resize-none"
                          placeholder="Saisissez l'énoncé de la question..."
                        />
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <Label>Points</Label>
                        <Input
                          type="number" min={0.5} step={0.5}
                          value={q.points}
                          onChange={(e) => updateQuestion(q.localId, { points: Number(e.target.value) })}
                          className="rounded-xl"
                        />
                      </div>
                    </div>

                    {/* ── Choix : QCM et quiz chronométré ── */}
                    {(q.type === "QCM" || q.type === "QUIZ_CHRONOMETREE") && (
                      <div className="flex flex-col gap-3">
                        <div className="flex items-center gap-2">
                          <Label>Choix multiples ?</Label>
                          <input
                            type="checkbox"
                            checked={q.estChoixMultiple ?? false}
                            onChange={(e) => updateQuestion(q.localId, basculerChoixMultiple(q, e.target.checked))}
                            className="size-4 accent-primary"
                          />
                        </div>
                        <div className="flex flex-col gap-2">
                          {q.choix?.map((c) => (
                            <div key={c.id} className="flex items-center gap-2">
                              <input
                                type={q.estChoixMultiple ? "checkbox" : "radio"}
                                checked={c.estCorrect}
                                onChange={(e) => {
                                  if (!q.estChoixMultiple) {
                                    // radio : un seul correct
                                    q.choix?.forEach(ch => updateChoix(q.localId, ch.id, { estCorrect: ch.id === c.id ? e.target.checked : false }))
                                  } else {
                                    updateChoix(q.localId, c.id, { estCorrect: e.target.checked })
                                  }
                                }}
                                className="size-4 shrink-0 accent-primary"
                              />
                              <Input
                                value={c.texte}
                                onChange={(e) => updateChoix(q.localId, c.id, { texte: e.target.value })}
                                placeholder={`Choix ${q.choix?.indexOf(c)! + 1}`}
                                className="rounded-xl flex-1"
                              />
                              <button onClick={() => removeChoix(q.localId, c.id)} className="shrink-0 text-muted-foreground hover:text-destructive">
                                <Trash2 className="size-4" />
                              </button>
                            </div>
                          ))}
                          <button onClick={() => addChoix(q.localId)} className="flex items-center gap-1 text-xs text-primary">
                            <Plus className="size-3" /> Ajouter un choix
                          </button>
                        </div>
                        <p className="text-[11px] text-muted-foreground">
                          Cochez la/les bonne(s) réponse(s).
                        </p>
                      </div>
                    )}

                    {/* ── OUVERTE ── */}
                    {q.type === "OUVERTE" && (
                      <p className="rounded-xl bg-muted px-4 py-3 text-sm text-muted-foreground">
                        La réponse sera saisie librement par l'étudiant. La correction sera assistée par l'IA et validée par vous.
                      </p>
                    )}

                    {/* ── CODE ── */}
                    {q.type === "CODE" && (
                      <div className="flex flex-col gap-3">
                        <Label>Cas de test</Label>
                        {q.casTests?.map((ct, i) => (
                          <div key={ct.id} className="rounded-xl border border-border p-3">
                            <div className="mb-2 flex items-center justify-between">
                              <span className="text-xs font-semibold text-muted-foreground">Cas #{i + 1}</span>
                              <div className="flex items-center gap-3">
                                <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                  <input
                                    type="checkbox"
                                    checked={ct.estMasque}
                                    onChange={(e) => updateCasTest(q.localId, ct.id, { estMasque: e.target.checked })}
                                    className="size-3.5 accent-primary"
                                  />
                                  Masqué
                                </label>
                                <button onClick={() => removeCasTest(q.localId, ct.id)} className="text-muted-foreground hover:text-destructive">
                                  <Trash2 className="size-3.5" />
                                </button>
                              </div>
                            </div>
                            <div className="grid gap-2 md:grid-cols-2">
                              <div className="flex flex-col gap-1">
                                <Label className="text-[11px]">Entrée</Label>
                                <Input value={ct.entree} onChange={(e) => updateCasTest(q.localId, ct.id, { entree: e.target.value })} className="rounded-lg font-mono text-xs" placeholder="stdin" />
                              </div>
                              <div className="flex flex-col gap-1">
                                <Label className="text-[11px]">Sortie attendue</Label>
                                <Input value={ct.sortieAttendue} onChange={(e) => updateCasTest(q.localId, ct.id, { sortieAttendue: e.target.value })} className="rounded-lg font-mono text-xs" placeholder="stdout attendu" />
                              </div>
                            </div>
                          </div>
                        ))}
                        <button onClick={() => addCasTest(q.localId)} className="flex items-center gap-1 text-xs text-primary">
                          <Plus className="size-3" /> Ajouter un cas de test
                        </button>
                      </div>
                    )}

                    {/* ── QUIZ CHRONOMÉTRÉ ── */}
                    {q.type === "QUIZ_CHRONOMETREE" && (
                      <div className="flex flex-col gap-1.5">
                        <Label>Durée individuelle (secondes)</Label>
                        <Input
                          type="number" min={5}
                          value={q.dureeQuestion ?? 30}
                          onChange={(e) => updateQuestion(q.localId, { dureeQuestion: Number(e.target.value) })}
                          className="w-40 rounded-xl"
                        />
                        <p className="text-[11px] text-muted-foreground">
                          L'étudiant a ce temps pour répondre à cette question spécifiquement.
                        </p>
                      </div>
                    )}

                  </CardContent>
                )}
              </Card>
            )
          })}
        </div>
      )}

      {tentative && nbInvalides > 0 && (
        <p className="text-sm text-destructive">
          {nbInvalides} question{nbInvalides > 1 ? "s sont incomplètes" : " est incomplète"} : corrigez-la{nbInvalides > 1 ? "s" : ""} avant de continuer.
        </p>
      )}

      {/* Navigation */}
      <div className="flex justify-between gap-3">
        <Button
          variant="outline" className="rounded-xl"
          onClick={() => navigate(chemin("infos"))}
        >
          ← Retour
        </Button>
        <Button
          className="rounded-xl"
          disabled={questions.length === 0}
          onClick={handleNext}
        >
          Suivant — Prévisualisation <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  )
}
