package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.etudiant.EvaluationEtudiantResponse;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.AmenagementTempsRepository;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Évaluations visibles par l'étudiant connecté (Diagramme de Séquence DS-10). */
@Service
@Transactional(readOnly = true)
public class EvaluationEtudiantService {

    /** Les brouillons restent invisibles pour les étudiants. */
    private static final List<StatutEvaluation> STATUTS_VISIBLES =
            List.of(StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS, StatutEvaluation.TERMINEE);

    private final SoumissionAccesService soumissionAccesService;
    private final EvaluationRepository evaluationRepository;
    private final SoumissionRepository soumissionRepository;
    private final AmenagementTempsRepository amenagementTempsRepository;

    public EvaluationEtudiantService(SoumissionAccesService soumissionAccesService,
                                     EvaluationRepository evaluationRepository,
                                     SoumissionRepository soumissionRepository,
                                     AmenagementTempsRepository amenagementTempsRepository) {
        this.soumissionAccesService = soumissionAccesService;
        this.evaluationRepository = evaluationRepository;
        this.soumissionRepository = soumissionRepository;
        this.amenagementTempsRepository = amenagementTempsRepository;
    }

    /** Évaluations de la promotion de l'étudiant, les plus récentes d'abord, avec l'état de sa copie. */
    public List<EvaluationEtudiantResponse> mesEvaluations(String email) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(email);
        if (etudiant.getPromotion() == null) {
            return List.of();
        }

        // Copies et aménagements de l'étudiant chargés en une requête chacun (pas de N+1)
        Map<Long, Soumission> copies = soumissionRepository.findByEtudiantId(etudiant.getId()).stream()
                .collect(Collectors.toMap(s -> s.getEvaluation().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Integer> amenagements = amenagementTempsRepository.findByEtudiantId(etudiant.getId()).stream()
                .collect(Collectors.toMap(a -> a.getEvaluation().getId(), AmenagementTemps::getDureePersonnalisee, (a, b) -> a));

        return evaluationRepository
                .findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(etudiant.getPromotion().getId(), STATUTS_VISIBLES)
                .stream()
                .map(e -> versResponse(e, copies.get(e.getId()), amenagements.get(e.getId())))
                .toList();
    }

    private EvaluationEtudiantResponse versResponse(Evaluation evaluation, Soumission copie, Integer dureeAmenagee) {
        EvaluationEtudiantResponse r = new EvaluationEtudiantResponse();
        r.setId(evaluation.getId());
        r.setTitre(evaluation.getTitre());
        r.setDescription(evaluation.getDescription());
        r.setStatut(evaluation.getStatut());
        r.setDateOuverture(evaluation.getDateOuverture());
        r.setDateFermeture(evaluation.getDateFermeture());
        r.setDureeMinutes(dureeAmenagee != null ? dureeAmenagee : evaluation.getDureeStandard());
        r.setResultatsPublies(evaluation.getResultatsPublies());

        List<Question> questions = evaluation.getQuestions();
        r.setNbQuestions(questions.size());
        r.setTypesQuestions(questions.stream().map(Question::getType).distinct().toList());
        questions.stream()
                .map(Hibernate::unproxy)
                .filter(QuestionQuiz.class::isInstance)
                .map(q -> ((QuestionQuiz) q).getDureeQuestion())
                .findFirst()
                .ifPresent(r::setDureeQuestion);

        if (copie != null) {
            r.setSoumissionId(copie.getId());
            r.setStatutSoumission(copie.getStatut());
        }
        return r;
    }
}
