package sn.ept.eval.dto.evaluation;

import java.time.LocalDateTime;

/** Publication des résultats programmée (DS-07). */
public record ProgrammationPublicationResponse(Long evaluationId, LocalDateTime publicationPrevueLe) {}
