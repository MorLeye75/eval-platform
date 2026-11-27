package sn.ept.eval.dto.correction;

/** Résultat d'une validation : la note retenue et, si c'était la dernière, la note finale de la copie. */
public record NoteResponse(Long reponseId, double noteRetenue, boolean copieEvaluee, Double noteFinale) {}
