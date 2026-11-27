package sn.ept.eval.dto.soumission;

/** Cas de test non masqué, affiché à l'étudiant pendant l'épreuve. */
public class CasDeTestVisibleDto {

    private Long id;
    private String entree;
    private String sortieAttendue;

    public CasDeTestVisibleDto() {}

    public CasDeTestVisibleDto(Long id, String entree, String sortieAttendue) {
        this.id = id;
        this.entree = entree;
        this.sortieAttendue = sortieAttendue;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntree() { return entree; }
    public void setEntree(String entree) { this.entree = entree; }

    public String getSortieAttendue() { return sortieAttendue; }
    public void setSortieAttendue(String sortieAttendue) { this.sortieAttendue = sortieAttendue; }
}
