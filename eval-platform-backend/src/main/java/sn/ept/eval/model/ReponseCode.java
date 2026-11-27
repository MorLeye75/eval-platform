package sn.ept.eval.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reponses_code")
public class ReponseCode extends ReponseEtudiant {

    @Column(columnDefinition = "TEXT")
    private String codeSoumis;

    /** Langage choisi par l'étudiant (c, cpp, java, python, javascript), utilisé pour exécuter le code (V6). */
    @Column(length = 20)
    private String langage;

    @OneToMany(mappedBy = "reponseCode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultatCasTest> resultatsCasTests = new ArrayList<>();

    public ReponseCode() {
        super();
    }

    public String getCodeSoumis() { return codeSoumis; }
    public void setCodeSoumis(String codeSoumis) { this.codeSoumis = codeSoumis; }

    public String getLangage() { return langage; }
    public void setLangage(String langage) { this.langage = langage; }

    public List<ResultatCasTest> getResultatsCasTests() { return resultatsCasTests; }
    public void setResultatsCasTests(List<ResultatCasTest> resultatsCasTests) { this.resultatsCasTests = resultatsCasTests; }

    public void addResultat(ResultatCasTest r) {
        resultatsCasTests.add(r);
        r.setReponseCode(this);
    }
}
