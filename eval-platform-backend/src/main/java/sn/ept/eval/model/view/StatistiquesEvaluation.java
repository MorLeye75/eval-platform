package sn.ept.eval.model.view;

/**
 * Vue représentant les statistiques calculées d'une évaluation («View» StatistiquesEvaluation)
 */
public class StatistiquesEvaluation {

    private Double moyenne;
    private Double noteMax;
    private Double noteMin;
    private Double ecartType;
    private Double tauxReussite;

    public StatistiquesEvaluation() {}

    public StatistiquesEvaluation(Double moyenne, Double noteMax, Double noteMin, Double ecartType, Double tauxReussite) {
        this.moyenne = moyenne;
        this.noteMax = noteMax;
        this.noteMin = noteMin;
        this.ecartType = ecartType;
        this.tauxReussite = tauxReussite;
    }

    public Double getMoyenne() {
        return moyenne;
    }

    public void setMoyenne(Double moyenne) {
        this.moyenne = moyenne;
    }

    public Double getNoteMax() {
        return noteMax;
    }

    public void setNoteMax(Double noteMax) {
        this.noteMax = noteMax;
    }

    public Double getNoteMin() {
        return noteMin;
    }

    public void setNoteMin(Double noteMin) {
        this.noteMin = noteMin;
    }

    public Double getEcartType() {
        return ecartType;
    }

    public void setEcartType(Double ecartType) {
        this.ecartType = ecartType;
    }

    public Double getTauxReussite() {
        return tauxReussite;
    }

    public void setTauxReussite(Double tauxReussite) {
        this.tauxReussite = tauxReussite;
    }
}
