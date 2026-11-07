package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import sn.ept.eval.dto.soumission.ReponseItemDto;
import sn.ept.eval.model.*;
import sn.ept.eval.repository.ChoixReponseRepository;
import sn.ept.eval.repository.QuestionRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validation et enregistrement des réponses d'une copie (création ou mise à jour), selon le type
 * de question : choix cochés (QCM, quiz chronométré), code ou texte libre.
 * Appelé dans la transaction de la sauvegarde ; les règles d'état et de temps sont vérifiées en amont.
 */
@Service
public class EnregistrementReponsesService {

    private final QuestionRepository questionRepository;
    private final ChoixReponseRepository choixReponseRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;

    public EnregistrementReponsesService(QuestionRepository questionRepository,
                                         ChoixReponseRepository choixReponseRepository,
                                         ReponseEtudiantRepository reponseEtudiantRepository) {
        this.questionRepository = questionRepository;
        this.choixReponseRepository = choixReponseRepository;
        this.reponseEtudiantRepository = reponseEtudiantRepository;
    }

    /** @return le nombre de réponses enregistrées */
    public int enregistrer(Soumission soumission, List<ReponseItemDto> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        refuserQuestionsEnDouble(items);

        // Questions de l'évaluation et réponses existantes chargées en une requête chacune (pas de N+1)
        Map<Long, Question> questionsEvaluation = questionRepository
                .findByEvaluationIdOrderByOrdreAsc(soumission.getEvaluation().getId()).stream()
                .collect(Collectors.toMap(Question::getId, q -> (Question) Hibernate.unproxy(q)));
        Map<Long, ReponseEtudiant> reponsesExistantes = reponseEtudiantRepository.findBySoumissionId(soumission.getId()).stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (r1, r2) -> r1));

        int nbSauvegardees = 0;
        for (ReponseItemDto item : items) {
            if (item.getQuestionId() == null) continue;

            Question question = questionsEvaluation.get(item.getQuestionId());
            if (question == null) {
                // La question doit appartenir à l'évaluation de cette copie. Même refus qu'elle existe ailleurs ou
                // non : une réponse différente révélerait quels identifiants de question existent.
                throw new IllegalArgumentException("La question " + item.getQuestionId() + " n'appartient pas à cette évaluation.");
            }

            ReponseEtudiant reponse = reponsesExistantes.get(question.getId());

            // Une réponse existante d'un autre type (ex. question modifiée) est remplacée, pas dupliquée
            if (reponse != null && !estDuBonType(reponse, question)) {
                soumission.getReponses().remove(reponse);
                reponseEtudiantRepository.delete(reponse);
                reponseEtudiantRepository.flush();
                reponse = null;
            }

            // Dispatch sur la classe : QuestionQuiz hérite de QuestionQCM (même stockage par choix cochés)
            if (question instanceof QuestionQCM questionQcm) {
                ReponseQCM rq = (reponse instanceof ReponseQCM) ? (ReponseQCM) reponse : new ReponseQCM();
                rq.setSoumission(soumission);
                rq.setQuestion(question);
                Set<ChoixReponse> coches = (item.getChoixIds() != null && !item.getChoixIds().isEmpty())
                        ? getChoixValides(questionQcm, item.getChoixIds())
                        : Set.of();
                // Mise à jour sur place : Hibernate ne supprime et n'insère que les choix qui changent
                rq.getChoixCoches().retainAll(coches);
                rq.getChoixCoches().addAll(coches);
                reponseEtudiantRepository.save(rq);
            } else if (question instanceof QuestionCode) {
                ReponseCode rc = (reponse instanceof ReponseCode) ? (ReponseCode) reponse : new ReponseCode();
                rc.setSoumission(soumission);
                rc.setQuestion(question);
                rc.setCodeSoumis(item.getCodeSoumis());
                reponseEtudiantRepository.save(rc);
            } else {
                ReponseOuverte ro = (reponse instanceof ReponseOuverte) ? (ReponseOuverte) reponse : new ReponseOuverte();
                ro.setSoumission(soumission);
                ro.setQuestion(question);
                ro.setTexteSoumis(item.getTexteSoumis());
                reponseEtudiantRepository.save(ro);
            }
            nbSauvegardees++;
        }
        return nbSauvegardees;
    }

    /**
     * Une question ne peut apparaître qu'une fois par sauvegarde : sinon chaque occurrence créerait sa propre
     * réponse, toutes notées puis additionnées dans la note finale (HTTP 400).
     */
    private void refuserQuestionsEnDouble(List<ReponseItemDto> items) {
        Set<Long> vues = new HashSet<>();
        for (ReponseItemDto item : items) {
            if (item.getQuestionId() != null && !vues.add(item.getQuestionId())) {
                throw new IllegalArgumentException("La question " + item.getQuestionId()
                        + " apparaît plusieurs fois dans la sauvegarde.");
            }
        }
    }

    private Set<ChoixReponse> getChoixValides(QuestionQCM question, List<Long> choixIds) {
        Set<Long> idsDemandes = new HashSet<>(choixIds);
        List<ChoixReponse> choix = choixReponseRepository.findAllById(idsDemandes);
        boolean tousValides = choix.size() == idsDemandes.size()
                && choix.stream().allMatch(c -> c.getQuestion().getId().equals(question.getId()));
        if (!tousValides) {
            throw new IllegalArgumentException("Choix invalides pour la question " + question.getId() + ".");
        }
        if (!Boolean.TRUE.equals(question.getEstChoixMultiple()) && choix.size() > 1) {
            throw new IllegalArgumentException("La question " + question.getId() + " n'accepte qu'un seul choix.");
        }
        return new HashSet<>(choix);
    }

    private boolean estDuBonType(ReponseEtudiant reponse, Question question) {
        if (question instanceof QuestionQCM) return reponse instanceof ReponseQCM;
        if (question instanceof QuestionCode) return reponse instanceof ReponseCode;
        return reponse instanceof ReponseOuverte;
    }
}
