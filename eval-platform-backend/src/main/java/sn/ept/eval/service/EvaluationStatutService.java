package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Passage automatique des statuts d'une évaluation (DS-05), appelé par PassationScheduler.
 * PUBLIEE → EN_COURS à l'ouverture, EN_COURS → TERMINEE à la fermeture ; une évaluation publiée dont la
 * fermeture est déjà passée va directement à TERMINEE. Ne soumet aucune copie (voir DS-01 et ClotureCopiesService).
 */
@Service
public class EvaluationStatutService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationStatutService.class);

    private final EvaluationRepository evaluationRepository;
    private final Clock clock;

    public EvaluationStatutService(EvaluationRepository evaluationRepository, Clock clock) {
        this.evaluationRepository = evaluationRepository;
        this.clock = clock;
    }

    /** @return le nombre d'évaluations dont le statut a changé */
    @Transactional
    public int mettreAJourStatuts() {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        int modifiees = 0;

        // Les fermetures d'abord : une publiée déjà fermée ne doit pas passer par EN_COURS
        modifiees += changerStatut(
                evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.PUBLIEE, maintenant),
                StatutEvaluation.TERMINEE);
        modifiees += changerStatut(
                evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, maintenant),
                StatutEvaluation.EN_COURS);
        // TODO notification EVALUATION_OUVERTE ici, quand NotificationService existera
        modifiees += changerStatut(
                evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.EN_COURS, maintenant),
                StatutEvaluation.TERMINEE);
        return modifiees;
    }

    private int changerStatut(List<Evaluation> evaluations, StatutEvaluation nouveau) {
        for (Evaluation evaluation : evaluations) {
            log.info("[Statut] Évaluation ID={} : {} -> {}", evaluation.getId(), evaluation.getStatut(), nouveau);
            evaluation.setStatut(nouveau);
        }
        evaluationRepository.saveAllAndFlush(evaluations);
        return evaluations.size();
    }
}
