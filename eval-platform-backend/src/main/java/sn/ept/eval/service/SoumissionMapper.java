package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import sn.ept.eval.dto.soumission.*;
import sn.ept.eval.model.*;
import sn.ept.eval.repository.QuestionRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conversion des copies en DTO de passation. Ne renvoie jamais les bonnes réponses (estCorrect)
 * ni les cas de test masqués : ces DTO sont destinés à l'étudiant pendant l'épreuve.
 * Doit être appelé dans une transaction (chargement LAZY des choix et des réponses).
 */
@Component
public class SoumissionMapper {

    private final QuestionRepository questionRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final TempsEpreuveService tempsEpreuveService;
    private final QuizService quizService;

    public SoumissionMapper(QuestionRepository questionRepository,
                            ReponseEtudiantRepository reponseEtudiantRepository,
                            TempsEpreuveService tempsEpreuveService,
                            QuizService quizService) {
        this.quizService = quizService;
        this.questionRepository = questionRepository;
        this.reponseEtudiantRepository = reponseEtudiantRepository;
        this.tempsEpreuveService = tempsEpreuveService;
    }

    /**
     * Copie pour son étudiant en composition : temps restant, questions ordonnées et réponses déjà enregistrées.
     * Quiz chronométré : seule la question en cours.
     */
    public SoumissionDetailResponse versDetail(Soumission soumission) {
        return versDetail(soumission, false);
    }

    /**
     * Copie pour l'enseignant de l'évaluation ou l'administrateur : toutes les questions et réponses, même d'un quiz
     * (dont la copie remise n'a plus de question en cours). Toujours sans les bonnes réponses ni les cas masqués.
     */
    public SoumissionDetailResponse versDetailComplet(Soumission soumission) {
        return versDetail(soumission, true);
    }

    private SoumissionDetailResponse versDetail(Soumission soumission, boolean toutesLesQuestions) {
        SoumissionDetailResponse resp = versDetailSansQuestions(soumission);

        List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(soumission.getEvaluation().getId());
        Map<Long, ReponseEtudiant> reponsesParQuestion = reponseEtudiantRepository.findBySoumissionId(soumission.getId()).stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (r1, r2) -> r1));
        List<QuestionQuiz> questionsQuiz = QuizService.questionsQuiz(questions);

        // Durée effective déjà calculée et questions déjà lues : ni l'aménagement ni les questions ne sont relus
        resp.setTempsRestantSecondes(tempsEpreuveService.tempsRestantSecondes(
                tempsEpreuveService.finEpreuve(soumission, resp.getDureeMinutes(), questionsQuiz)));

        // Quiz chronométré : seule la question en cours part vers le navigateur de l'étudiant
        if (!questionsQuiz.isEmpty()) {
            QuizService.EtatQuiz etat = quizService.etat(soumission, questionsQuiz);
            resp.setQuiz(new EtatQuizDto(etat.index(), etat.nbQuestions(), etat.dureeQuestion(),
                    quizService.tempsRestantQuestionSecondes(etat)));
            if (!toutesLesQuestions) {
                questions = etat.termine() ? List.of() : List.of(questionsQuiz.get(etat.index()));
            }
        }

        List<QuestionExamenDto> questionsDtos = new ArrayList<>();
        for (Question q : questions) {
            questionsDtos.add(versQuestionExamen(q, reponsesParQuestion.get(q.getId())));
        }
        resp.setQuestions(questionsDtos);
        return resp;
    }

    /** État de la copie sans questions ni réponses : copie remise, consultée hors passation. */
    public SoumissionDetailResponse versDetailSansQuestions(Soumission soumission) {
        Evaluation eval = soumission.getEvaluation();

        SoumissionDetailResponse resp = new SoumissionDetailResponse();
        resp.setId(soumission.getId());
        resp.setEvaluationId(eval.getId());
        resp.setTitreEvaluation(eval.getTitre());
        resp.setDescriptionEvaluation(eval.getDescription());
        resp.setStatut(soumission.getStatut());
        resp.setDateDebut(soumission.getDateDebut());
        resp.setDateRemise(soumission.getDateRemise());
        resp.setVersionReponses(soumission.getVersionReponses());
        resp.setDureeMinutes(tempsEpreuveService.dureeEffectiveMinutes(soumission));
        resp.setTempsRestantSecondes(0L);
        resp.setNbChangementsOnglet(soumission.getNbChangementsOnglet());
        return resp;
    }

    /** Résumé renvoyé après une soumission. */
    public SoumissionResponse versResponse(Soumission soumission, String message) {
        SoumissionResponse r = new SoumissionResponse();
        r.setId(soumission.getId());
        r.setEvaluationId(soumission.getEvaluation().getId());
        r.setTitreEvaluation(soumission.getEvaluation().getTitre());
        r.setStatut(soumission.getStatut());
        r.setDateDebut(soumission.getDateDebut());
        r.setDateRemise(soumission.getDateRemise());
        r.setDureeMinutes(tempsEpreuveService.dureeEffectiveMinutes(soumission));
        r.setNbChangementsOnglet(soumission.getNbChangementsOnglet());
        r.setMessage(message);
        return r;
    }

    private QuestionExamenDto versQuestionExamen(Question question, ReponseEtudiant reponse) {
        // Une question déjà chargée en proxy dans la session (via une réponse) n'est pas une QuestionQCM pour
        // instanceof : sans déballage, la copie partirait sans ses choix ni ses cas de test
        Question q = (Question) Hibernate.unproxy(question);
        QuestionExamenDto qDto = new QuestionExamenDto();
        if (q instanceof QuestionQuiz quiz) {
            qDto.setDureeQuestion(quiz.getDureeQuestion());
        }
        qDto.setId(q.getId());
        qDto.setEnonce(q.getEnonce());
        qDto.setOrdre(q.getOrdre());
        qDto.setPoints(q.getPoints());
        qDto.setType(q.getType());

        if (q instanceof QuestionQCM qQcm) {
            qDto.setEstChoixMultiple(qQcm.getEstChoixMultiple());
            qDto.setChoixPossibles(qQcm.getChoix().stream()
                    .map(c -> new ChoixItemDto(c.getId(), c.getTexte()))
                    .collect(Collectors.toList()));
        }

        if (q instanceof QuestionCode qCode) {
            qDto.setCasDeTestsVisibles(qCode.getCasTests().stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getEstMasque()))
                    .map(c -> new CasDeTestVisibleDto(c.getId(), c.getEntree(), c.getSortieAttendue()))
                    .collect(Collectors.toList()));
        }

        if (reponse != null) {
            qDto.setReponseActuelle(versReponseItem(q, reponse));
        }
        return qDto;
    }

    private ReponseItemDto versReponseItem(Question q, ReponseEtudiant reponse) {
        ReponseItemDto repDto = new ReponseItemDto();
        repDto.setQuestionId(q.getId());
        if (reponse instanceof ReponseQCM rq) {
            repDto.setChoixIds(rq.getChoixCoches().stream().map(ChoixReponse::getId).sorted().collect(Collectors.toList()));
        } else if (reponse instanceof ReponseCode rc) {
            repDto.setCodeSoumis(rc.getCodeSoumis());
            repDto.setLangage(rc.getLangage());
        } else if (reponse instanceof ReponseOuverte ro) {
            repDto.setTexteSoumis(ro.getTexteSoumis());
        }
        return repDto;
    }
}
