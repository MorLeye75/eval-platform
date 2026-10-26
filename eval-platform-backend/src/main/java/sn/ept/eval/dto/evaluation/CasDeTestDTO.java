package sn.ept.eval.dto.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cas de test d'une question de code (DS-03). */
public class CasDeTestDTO {

    @NotBlank(message = "L'entrée du cas de test est obligatoire")
    @Size(max = 5000, message = "L'entrée est limitée à 5000 caractères")
    private String entree;

    @NotBlank(message = "La sortie attendue est obligatoire")
    @Size(max = 5000, message = "La sortie attendue est limitée à 5000 caractères")
    private String sortieAttendue;

    /** Un cas masqué n'est jamais montré à l'étudiant avant la publication des résultats. */
    private Boolean estMasque = false;

    public CasDeTestDTO() {}

    public CasDeTestDTO(String entree, String sortieAttendue, Boolean estMasque) {
        this.entree = entree;
        this.sortieAttendue = sortieAttendue;
        this.estMasque = estMasque;
    }

    public String getEntree() { return entree; }
    public void setEntree(String entree) { this.entree = entree; }

    public String getSortieAttendue() { return sortieAttendue; }
    public void setSortieAttendue(String sortieAttendue) { this.sortieAttendue = sortieAttendue; }

    public Boolean getEstMasque() { return estMasque; }
    public void setEstMasque(Boolean estMasque) { this.estMasque = estMasque; }
}
