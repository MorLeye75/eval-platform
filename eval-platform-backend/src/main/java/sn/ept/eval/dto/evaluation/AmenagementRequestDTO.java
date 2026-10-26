package sn.ept.eval.dto.evaluation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corps du PUT d'un aménagement de temps (DS-06). */
public class AmenagementRequestDTO {

    @NotNull(message = "La durée est obligatoire")
    @Min(value = 1, message = "La durée doit être d'au moins 1 minute")
    private Integer dureeMinutes;

    public AmenagementRequestDTO() {}

    public AmenagementRequestDTO(Integer dureeMinutes) {
        this.dureeMinutes = dureeMinutes;
    }

    public Integer getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(Integer dureeMinutes) { this.dureeMinutes = dureeMinutes; }
}
