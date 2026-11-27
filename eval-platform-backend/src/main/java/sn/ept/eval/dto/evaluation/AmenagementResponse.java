package sn.ept.eval.dto.evaluation;

/** Aménagement de temps d'un étudiant pour une évaluation (DS-06). */
public class AmenagementResponse {

    private Long etudiantId;
    private Integer dureeMinutes;

    public AmenagementResponse() {}

    public AmenagementResponse(Long etudiantId, Integer dureeMinutes) {
        this.etudiantId = etudiantId;
        this.dureeMinutes = dureeMinutes;
    }

    public Long getEtudiantId() { return etudiantId; }
    public void setEtudiantId(Long etudiantId) { this.etudiantId = etudiantId; }

    public Integer getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(Integer dureeMinutes) { this.dureeMinutes = dureeMinutes; }
}
