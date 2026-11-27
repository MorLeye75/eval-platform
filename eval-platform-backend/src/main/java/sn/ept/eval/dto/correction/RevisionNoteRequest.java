package sn.ept.eval.dto.correction;

import jakarta.validation.constraints.Size;

/** Nouvelle note d'une réponse déjà notée et motif obligatoire (tracé, et montré à l'étudiant avec sa note). */
public record RevisionNoteRequest(Double note, @Size(max = 500) String motif) {}
