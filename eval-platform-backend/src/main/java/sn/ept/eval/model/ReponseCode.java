package sn.ept.eval.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reponses_code")
public class ReponseCode extends ReponseEtudiant {

    @Column(columnDefinition = "TEXT")
    private String codeSoumis;

    @OneToMany(mappedBy = "reponseCode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultatCasTest> resultatsCasTests = new ArrayList<>();

    public ReponseCode() {
        super();
    }

    public String getCodeSoumis() { return codeSoumis; }
    public void setCodeSoumis(String codeSoumis) { this.codeSoumis = codeSoumis; }

    public List<ResultatCasTest> getResultatsCasTests() { return resultatsCasTests; }
    public void setResultatsCasTests(List<ResultatCasTest> resultatsCasTests) { this.resultatsCasTests = resultatsCasTests; }

    public void addResultat(ResultatCasTest r) {
        resultatsCasTests.add(r);
        r.setReponseCode(this);
    }
}
