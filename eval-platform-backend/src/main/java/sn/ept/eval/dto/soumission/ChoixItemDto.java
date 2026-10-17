package sn.ept.eval.dto.soumission;

public class ChoixItemDto {

    private Long id;
    private String texte;

    public ChoixItemDto() {}

    public ChoixItemDto(Long id, String texte) {
        this.id = id;
        this.texte = texte;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }
}
