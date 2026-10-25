package sn.ept.eval.dto.soumission;

import java.time.LocalDateTime;

public class SauvegardeResponse {

    private LocalDateTime dateSauvegarde;
    private String message;
    private Integer nbReponses;
    private Integer versionReponses; // nouvelle version des réponses de la copie

    public SauvegardeResponse() {}

    public SauvegardeResponse(LocalDateTime dateSauvegarde, String message, Integer nbReponses, Integer versionReponses) {
        this.dateSauvegarde = dateSauvegarde;
        this.message = message;
        this.nbReponses = nbReponses;
        this.versionReponses = versionReponses;
    }

    public LocalDateTime getDateSauvegarde() { return dateSauvegarde; }
    public void setDateSauvegarde(LocalDateTime dateSauvegarde) { this.dateSauvegarde = dateSauvegarde; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getNbReponses() { return nbReponses; }
    public void setNbReponses(Integer nbReponses) { this.nbReponses = nbReponses; }

    public Integer getVersionReponses() { return versionReponses; }
    public void setVersionReponses(Integer versionReponses) { this.versionReponses = versionReponses; }
}
