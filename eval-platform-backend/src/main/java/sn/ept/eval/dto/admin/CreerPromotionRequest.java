package sn.ept.eval.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreerPromotionRequest(@NotBlank @Size(max = 100) String nom, @NotBlank @Size(max = 20) String annee) {}
