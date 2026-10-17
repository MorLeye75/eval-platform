package sn.ept.eval.model.view;

/**
 * Vue représentant les indicateurs du tableau de bord d'un étudiant («View» TableauDeBordEtudiant)
 */
public class TableauDeBordEtudiant {

    private Double moyenneGenerale;
    private Double tauxReussite;
    private Integer rang;

    public TableauDeBordEtudiant() {}

    public TableauDeBordEtudiant(Double moyenneGenerale, Double tauxReussite, Integer rang) {
        this.moyenneGenerale = moyenneGenerale;
        this.tauxReussite = tauxReussite;
        this.rang = rang;
    }

    public Double getMoyenneGenerale() {
        return moyenneGenerale;
    }

    public void setMoyenneGenerale(Double moyenneGenerale) {
        this.moyenneGenerale = moyenneGenerale;
    }

    public Double getTauxReussite() {
        return tauxReussite;
    }

    public void setTauxReussite(Double tauxReussite) {
        this.tauxReussite = tauxReussite;
    }

    public Integer getRang() {
        return rang;
    }

    public void setRang(Integer rang) {
        this.rang = rang;
    }
}
