package sn.ept.eval.dto.evaluation;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Date (heure locale de l'application) à laquelle les résultats d'une évaluation seront publiés (DS-07). */
public record ProgrammationPublicationRequest(@NotNull LocalDateTime datePublication) {}
