package sn.ept.eval.dto.evaluation;

/** Promotion proposée à l'enseignant dans l'assistant de création d'une évaluation (DS-03). */
public record PromotionResponse(Long id, String nom, String annee, long nbEtudiants) {}
