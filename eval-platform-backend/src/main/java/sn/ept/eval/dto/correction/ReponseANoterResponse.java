package sn.ept.eval.dto.correction;

/** Une réponse ouverte sans proposition IA, à noter à la main par l'enseignant (DS-08, section E). */
public record ReponseANoterResponse(
        Long reponseId, Long evaluationId, String evaluation, String etudiant, String question, String reponse, double pointsMax) {}
