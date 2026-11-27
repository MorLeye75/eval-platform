package sn.ept.eval.dto.correction;

/** Résultat d'une révision ; {@code noteFinale} est nulle tant que la copie n'est pas entièrement notée. */
public record RevisionNoteResponse(Long reponseId, double ancienneNote, double nouvelleNote, Double noteFinale) {}
