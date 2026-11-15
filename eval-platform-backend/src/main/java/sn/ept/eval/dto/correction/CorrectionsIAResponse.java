package sn.ept.eval.dto.correction;

import java.util.List;

/** Propositions listées, et nombre de réponses encore sans proposition (génération en cours ou échouée). */
public record CorrectionsIAResponse(List<PropositionIAResponse> propositions, long enCours) {}
