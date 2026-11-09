package sn.ept.eval.dto.evaluation;

/**
 * Bilan d'une publication de résultats (DS-07). Les copies pas encore notées ne sont pas bloquantes : leur étudiant
 * est notifié plus tard, quand sa dernière note est validée.
 */
public class ResultatsPublicationResponse {

    private Long evaluationId;
    private Boolean resultatsPublies;
    private Long nbCopies;
    private Long nbCopiesNotees;
    private Long nbCopiesEnAttente;

    public ResultatsPublicationResponse() {}

    public ResultatsPublicationResponse(Long evaluationId, Boolean resultatsPublies, Long nbCopies,
                                        Long nbCopiesNotees, Long nbCopiesEnAttente) {
        this.evaluationId = evaluationId;
        this.resultatsPublies = resultatsPublies;
        this.nbCopies = nbCopies;
        this.nbCopiesNotees = nbCopiesNotees;
        this.nbCopiesEnAttente = nbCopiesEnAttente;
    }

    public Long getEvaluationId() { return evaluationId; }
    public void setEvaluationId(Long evaluationId) { this.evaluationId = evaluationId; }

    public Boolean getResultatsPublies() { return resultatsPublies; }
    public void setResultatsPublies(Boolean resultatsPublies) { this.resultatsPublies = resultatsPublies; }

    /** Toutes les copies de l'évaluation, quel que soit leur statut. */
    public Long getNbCopies() { return nbCopies; }
    public void setNbCopies(Long nbCopies) { this.nbCopies = nbCopies; }

    /** Copies EVALUE : visibles immédiatement par leur étudiant, qui reçoit une notification. */
    public Long getNbCopiesNotees() { return nbCopiesNotees; }
    public void setNbCopiesNotees(Long nbCopiesNotees) { this.nbCopiesNotees = nbCopiesNotees; }

    /** Copies pas encore notées (correction en cours ou manuelle) : visibles dès qu'elles sont notées. */
    public Long getNbCopiesEnAttente() { return nbCopiesEnAttente; }
    public void setNbCopiesEnAttente(Long nbCopiesEnAttente) { this.nbCopiesEnAttente = nbCopiesEnAttente; }
}
