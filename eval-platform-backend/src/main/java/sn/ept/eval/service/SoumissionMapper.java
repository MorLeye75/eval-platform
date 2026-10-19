package sn.ept.eval.service;

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
 * ni les cas de test : ces DTO sont destinés à l'étudiant pendant l'épreuve.
 * Doit être appelé dans une transaction (chargement LAZY des choix et des réponses).
 */
@Component
public class SoumissionMapper {

    private final QuestionRepository questionRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final TempsEpreuveService tempsEpreuveService;

    public SoumissionMapper(QuestionRepository questionRepository,
                            ReponseEtudiantRepository reponseEtudiantRepository,
                            TempsEpreuveService tempsEpreuveService) {
        this.questionRepository = questionRepository;
        this.reponseEtudiantRepository = reponseEtudiantRepository;
        this.tempsEpreuveService = tempsEpreuveService;
    }

    /** Copie complète : temps restant, questions ordonnées et réponses déjà enregistrées. */
    public SoumissionDetailResponse versDetail(Soumission soumission) {
        Evaluation eval = soumission.getEvaluation();

        SoumissionDetailResponse resp = new SoumissionDetailResponse();
        resp.setId(soumission.getId());
        resp.setEvaluationId(eval.getId());
        resp.setTitreEvaluation(eval.getTitre());
        resp.setDescriptionEvaluation(eval.getDescription());
        resp.setStatut(soumission.getStatut());
        resp.setDateDebut(soumission.getDateDebut());
        resp.setDateRemise(soumission.getDateRemise());
        resp.setDureeMinutes(tempsEpreuveService.dureeEffectiveMinutes(soumission));
        resp.setTempsRestantSecondes(tempsEpreuveService.tempsRestantSecondes(soumission));
        resp.setNbChangementsOnglet(soumission.getNbChangementsOnglet());

        List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(eval.getId());
        Map<Long, ReponseEtudiant> reponsesParQuestion = reponseEtudiantRepository.findBySoumissionId(soumission.getId()).stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (r1, r2) -> r1));

        List<QuestionExamenDto> questionsDtos = new ArrayList<>();
        for (Question q : questions) {
            questionsDtos.add(versQuestionExamen(q, reponsesParQuestion.get(q.getId())));
        }
        resp.setQuestions(questionsDtos);
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

    private QuestionExamenDto versQuestionExamen(Question q, ReponseEtudiant reponse) {
        QuestionExamenDto qDto = new QuestionExamenDto();
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

        if (reponse != null) {
            qDto.setReponseActuelle(versReponseItem(q, reponse));
        }
        return qDto;
    }

    private ReponseItemDto versReponseItem(Question q, ReponseEtudiant reponse) {
        ReponseItemDto repDto = new ReponseItemDto();
        repDto.setQuestionId(q.getId());
        if (reponse instanceof ReponseQCM rq) {
            repDto.setChoixIds(rq.getChoixCoches().stream().map(ChoixReponse::getId).collect(Collectors.toList()));
        } else if (reponse instanceof ReponseCode rc) {
            repDto.setCodeSoumis(rc.getCodeSoumis());
        } else if (reponse instanceof ReponseOuverte ro) {
            repDto.setTexteSoumis(ro.getTexteSoumis());
        }
        return repDto;
    }
}
