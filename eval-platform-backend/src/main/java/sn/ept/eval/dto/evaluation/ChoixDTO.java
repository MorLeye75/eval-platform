package sn.ept.eval.dto.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Choix d'une question QCM ou quiz (DS-03). */
public class ChoixDTO {

    @NotBlank(message = "Le texte du choix est obligatoire")
    @Size(max = 1000, message = "Le texte du choix est limité à 1000 caractères")
    private String texte;

    @NotNull(message = "Précisez si le choix est correct")
    private Boolean estCorrect;

    public ChoixDTO() {}

    public ChoixDTO(String texte, Boolean estCorrect) {
        this.texte = texte;
        this.estCorrect = estCorrect;
    }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public Boolean getEstCorrect() { return estCorrect; }
    public void setEstCorrect(Boolean estCorrect) { this.estCorrect = estCorrect; }
}
