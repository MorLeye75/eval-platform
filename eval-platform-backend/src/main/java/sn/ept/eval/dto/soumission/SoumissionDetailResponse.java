package sn.ept.eval.dto.soumission;

import sn.ept.eval.model.enums.StatutSoumission;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SoumissionDetailResponse {

    private Long id;
    private Long evaluationId;
    private String titreEvaluation;
    private String descriptionEvaluation;
    private StatutSoumission statut;
    private LocalDateTime dateDebut;
    private LocalDateTime dateRemise;
    private Integer versionReponses;
    private Integer dureeMinutes;
    private Long tempsRestantSecondes;
    private Integer nbChangementsOnglet;
    private List<QuestionExamenDto> questions = new ArrayList<>();
    /** Quiz chronométré seulement : position dans le quiz (les questions ne contiennent que celle en cours). */
    private EtatQuizDto quiz;

    public SoumissionDetailResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEvaluationId() { return evaluationId; }
    public void setEvaluationId(Long evaluationId) { this.evaluationId = evaluationId; }

    public String getTitreEvaluation() { return titreEvaluation; }
    public void setTitreEvaluation(String titreEvaluation) { this.titreEvaluation = titreEvaluation; }

    public String getDescriptionEvaluation() { return descriptionEvaluation; }
    public void setDescriptionEvaluation(String descriptionEvaluation) { this.descriptionEvaluation = descriptionEvaluation; }

    public StatutSoumission getStatut() { return statut; }
    public void setStatut(StatutSoumission statut) { this.statut = statut; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateRemise() { return dateRemise; }
    public void setDateRemise(LocalDateTime dateRemise) { this.dateRemise = dateRemise; }

    public Integer getVersionReponses() { return versionReponses; }
    public void setVersionReponses(Integer versionReponses) { this.versionReponses = versionReponses; }

    public Integer getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(Integer dureeMinutes) { this.dureeMinutes = dureeMinutes; }

    public Long getTempsRestantSecondes() { return tempsRestantSecondes; }
    public void setTempsRestantSecondes(Long tempsRestantSecondes) { this.tempsRestantSecondes = tempsRestantSecondes; }

    public Integer getNbChangementsOnglet() { return nbChangementsOnglet; }
    public void setNbChangementsOnglet(Integer nbChangementsOnglet) { this.nbChangementsOnglet = nbChangementsOnglet; }

    public List<QuestionExamenDto> getQuestions() { return questions; }
    public void setQuestions(List<QuestionExamenDto> questions) { this.questions = questions; }

    public EtatQuizDto getQuiz() { return quiz; }
    public void setQuiz(EtatQuizDto quiz) { this.quiz = quiz; }
}
