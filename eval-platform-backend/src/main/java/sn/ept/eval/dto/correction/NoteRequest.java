package sn.ept.eval.dto.correction;

import jakarta.validation.constraints.Size;

/** Note de l'enseignant ; facultative pour « valider » (la note de l'IA est reprise), obligatoire sinon. */
public record NoteRequest(Double note, @Size(max = 2000) String commentaire) {}
