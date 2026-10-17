package sn.ept.eval.dto.soumission;

import sn.ept.eval.model.enums.StatutSoumission;

import java.time.LocalDateTime;

public class SoumissionResponse {

    private Long id;
    private Long evaluationId;
    private String titreEvaluation;
    private StatutSoumission statut;
    private LocalDateTime dateDebut;
    private LocalDateTime dateRemise;
    private Integer dureeMinutes;
    private Long tempsRestantSecondes;
    private Integer nbChangementsOnglet;
    private String message;

    public SoumissionResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEvaluationId() { return evaluationId; }
    public void setEvaluationId(Long evaluationId) { this.evaluationId = evaluationId; }

    public String getTitreEvaluation() { return titreEvaluation; }
    public void setTitreEvaluation(String titreEvaluation) { this.titreEvaluation = titreEvaluation; }

    public StatutSoumission getStatut() { return statut; }
    public void setStatut(StatutSoumission statut) { this.statut = statut; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateRemise() { return dateRemise; }
    public void setDateRemise(LocalDateTime dateRemise) { this.dateRemise = dateRemise; }

    public Integer getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(Integer dureeMinutes) { this.dureeMinutes = dureeMinutes; }

    public Long getTempsRestantSecondes() { return tempsRestantSecondes; }
    public void setTempsRestantSecondes(Long tempsRestantSecondes) { this.tempsRestantSecondes = tempsRestantSecondes; }

    public Integer getNbChangementsOnglet() { return nbChangementsOnglet; }
    public void setNbChangementsOnglet(Integer nbChangementsOnglet) { this.nbChangementsOnglet = nbChangementsOnglet; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
