package sn.ept.eval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import sn.ept.eval.model.enums.VerdictCas;

@Entity
@Table(name = "resultats_cas_tests")
public class ResultatCasTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String sortieObtenue;

    @Column(nullable = false)
    private Double tempsExecution;

    @Column(nullable = false)
    private Boolean estValide;

    @Column(columnDefinition = "TEXT")
    private String messageErreur;

    /** Verdict déduit du statut Judge0 (V8) ; nul pour les résultats antérieurs. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private VerdictCas verdict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cas_de_test_id", nullable = false)
    private CasDeTest casDeTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reponse_code_id", nullable = false)
    @JsonIgnore
    private ReponseCode reponseCode;

    public ResultatCasTest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSortieObtenue() { return sortieObtenue; }
    public void setSortieObtenue(String sortieObtenue) { this.sortieObtenue = sortieObtenue; }

    public Double getTempsExecution() { return tempsExecution; }
    public void setTempsExecution(Double tempsExecution) { this.tempsExecution = tempsExecution; }

    public Boolean getEstValide() { return estValide; }
    public void setEstValide(Boolean estValide) { this.estValide = estValide; }

    public String getMessageErreur() { return messageErreur; }
    public void setMessageErreur(String messageErreur) { this.messageErreur = messageErreur; }

    public VerdictCas getVerdict() { return verdict; }
    public void setVerdict(VerdictCas verdict) { this.verdict = verdict; }

    public CasDeTest getCasDeTest() { return casDeTest; }
    public void setCasDeTest(CasDeTest casDeTest) { this.casDeTest = casDeTest; }

    public ReponseCode getReponseCode() { return reponseCode; }
    public void setReponseCode(ReponseCode reponseCode) { this.reponseCode = reponseCode; }
}
