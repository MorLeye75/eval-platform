package sn.ept.eval.dto.correction;

import sn.ept.eval.model.enums.StatutProposition;

/** Une proposition de l'IA pour une réponse ouverte, avec de quoi la valider (DS-08). */
public record PropositionIAResponse(
        Long id, Long reponseId, Long evaluationId, String evaluation, String etudiant, String question,
        String reponse, double pointsMax, double noteProposee, String justification,
        StatutProposition statut, Double noteRetenue) {}
