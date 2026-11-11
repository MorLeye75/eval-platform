package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.model.Question;
import sn.ept.eval.model.QuestionQuiz;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.repository.QuestionRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Règles du quiz chronométré, imposées par le serveur : les questions se suivent dans l'ordre, chacune dans son
 * temps. Une question dont le temps (marge réseau comprise) est écoulé est close et la suivante commence à sa
 * fin, même si l'étudiant n'était plus connecté. Seule la question en cours peut recevoir une réponse.
 */
@Service
public class QuizService {

    /** Délai accordé après la fin d'une question (latence réseau du dernier envoi). */
    public static final int MARGE_QUESTION_SECONDES = 2;

    private final QuestionRepository questionRepository;
    private final TempsEpreuveService tempsEpreuveService;

    public QuizService(QuestionRepository questionRepository, TempsEpreuveService tempsEpreuveService) {
        this.questionRepository = questionRepository;
        this.tempsEpreuveService = tempsEpreuveService;
    }

    /**
     * Position de la copie dans le quiz.
     *
     * @param debutQuestion heure de début de la question en cours (ou de fin de la dernière si le quiz est terminé)
     */
    public record EtatQuiz(int index, int nbQuestions, LocalDateTime debutQuestion, Integer dureeQuestion) {
        public boolean termine() {
            return index >= nbQuestions;
        }
    }

    /** Questions du quiz dans l'ordre, ou liste vide si l'évaluation n'est pas un quiz (toutes ses questions). */
    public static List<QuestionQuiz> questionsQuiz(List<Question> questions) {
        List<Object> reelles = questions.stream().map(q -> (Object) Hibernate.unproxy(q)).toList();
        if (reelles.isEmpty() || !reelles.stream().allMatch(QuestionQuiz.class::isInstance)) return List.of();
        return reelles.stream().map(QuestionQuiz.class::cast).toList();
    }

    public List<QuestionQuiz> questionsQuiz(Soumission soumission) {
        return questionsQuiz(questionRepository.findByEvaluationIdOrderByOrdreAsc(soumission.getEvaluation().getId()));
    }

    /**
     * Questions de quiz de plusieurs évaluations en une requête (évaluation → questions, vide si ce n'est pas un
     * quiz). Pour la clôture en lot des copies (DS-11).
     */
    public Map<Long, List<QuestionQuiz>> questionsQuizParEvaluation(Collection<Long> evaluationIds) {
        if (evaluationIds.isEmpty()) return Map.of();
        Map<Long, List<Question>> parEvaluation = new HashMap<>();
        for (Question q : questionRepository.findByEvaluationIdInOrderByOrdreAsc(evaluationIds)) {
            parEvaluation.computeIfAbsent(q.getEvaluation().getId(), id -> new ArrayList<>()).add(q);
        }
        Map<Long, List<QuestionQuiz>> resultat = new HashMap<>();
        for (Long id : evaluationIds) {
            resultat.put(id, questionsQuiz(parEvaluation.getOrDefault(id, List.of())));
        }
        return resultat;
    }

    /**
     * Fin du quiz si l'étudiant laisse courir le temps : début de la question en cours + durées de celle-ci et des
     * suivantes (fin de la dernière question si le quiz est terminé). Ne dépend pas de l'instant : clore une
     * question écoulée (voir {@link #etat}) avance le début de la suivante d'autant que sa durée.
     */
    public static LocalDateTime finQuiz(Soumission soumission, List<QuestionQuiz> questions) {
        int index = soumission.getIndexQuestionQuiz() != null ? soumission.getIndexQuestionQuiz() : 0;
        LocalDateTime fin = soumission.getDebutQuestionQuiz() != null
                ? soumission.getDebutQuestionQuiz() : soumission.getDateDebut();
        for (int i = index; i < questions.size(); i++) {
            fin = fin.plusSeconds(duree(questions.get(i)));
        }
        return fin;
    }

    /** État à cet instant : les questions dont le temps est écoulé sont closes (sans rien enregistrer). */
    public EtatQuiz etat(Soumission soumission, List<QuestionQuiz> questions) {
        int index = soumission.getIndexQuestionQuiz() != null ? soumission.getIndexQuestionQuiz() : 0;
        LocalDateTime debut = soumission.getDebutQuestionQuiz() != null
                ? soumission.getDebutQuestionQuiz() : soumission.getDateDebut();
        LocalDateTime maintenant = tempsEpreuveService.maintenant();
        while (index < questions.size()) {
            LocalDateTime fin = debut.plusSeconds(duree(questions.get(index)));
            if (!maintenant.isAfter(fin.plusSeconds(MARGE_QUESTION_SECONDES))) break;
            debut = fin; // la question suivante commence à la fin de la précédente
            index++;
        }
        Integer duree = index < questions.size() ? duree(questions.get(index)) : null;
        return new EtatQuiz(index, questions.size(), debut, duree);
    }

    /** Temps restant sur la question en cours (0 si le quiz est terminé). */
    public long tempsRestantQuestionSecondes(EtatQuiz etat) {
        if (etat.termine()) return 0;
        LocalDateTime fin = etat.debutQuestion().plusSeconds(etat.dureeQuestion());
        return Math.max(0, Duration.between(tempsEpreuveService.maintenant(), fin).getSeconds());
    }

    /**
     * Réponses que la sauvegarde peut enregistrer : celle de la question en cours seulement. Une question passée
     * (retour en arrière) ou future (réponse en avance) est ignorée. L'appelant détient le verrou sur la copie.
     */
    public List<ReponseItemDto> reponsesAutorisees(Soumission soumission, List<QuestionQuiz> questions,
                                                   List<ReponseItemDto> items) {
        EtatQuiz etat = rattraper(soumission, questions);
        if (etat.termine() || items == null) return List.of();
        Long enCours = questions.get(etat.index()).getId();
        return items.stream().filter(i -> enCours.equals(i.getQuestionId())).toList();
    }

    /**
     * Passe à la question suivante si la copie est encore sur `indexActuel` : un double clic ou un nouvel essai
     * après une réponse perdue n'avance pas deux fois. L'appelant détient le verrou sur la copie.
     */
    public EtatQuiz passerALaSuivante(Soumission soumission, List<QuestionQuiz> questions, int indexActuel) {
        EtatQuiz etat = rattraper(soumission, questions);
        if (!etat.termine() && etat.index() == indexActuel) {
            soumission.setIndexQuestionQuiz(indexActuel + 1);
            soumission.setDebutQuestionQuiz(tempsEpreuveService.maintenant());
            return etat(soumission, questions);
        }
        return etat;
    }

    /** Enregistre sur la copie les questions closes par le temps. */
    private EtatQuiz rattraper(Soumission soumission, List<QuestionQuiz> questions) {
        EtatQuiz etat = etat(soumission, questions);
        soumission.setIndexQuestionQuiz(etat.index());
        soumission.setDebutQuestionQuiz(etat.debutQuestion());
        return etat;
    }

    private static int duree(QuestionQuiz question) {
        return question.getDureeQuestion() != null ? question.getDureeQuestion() : 30;
    }
}
