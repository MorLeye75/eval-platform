package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.EvaluationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Passage automatique des statuts d'une évaluation (DS-05), appelé par PassationScheduler.
 * PUBLIEE → EN_COURS à l'ouverture, EN_COURS → TERMINEE à la fermeture ; une évaluation publiée dont la
 * fermeture est déjà passée va directement à TERMINEE. Ne soumet aucune copie (voir DS-01 et DS-11) et ne
 * publie pas les résultats (DS-07).
 *
 * <p>Chaque transition est une mise à jour conditionnelle (« … WHERE statut = ancien ») dans sa propre
 * transaction : une évaluation en échec n'empêche pas les autres, et la notification « évaluation ouverte »
 * n'est envoyée que par le passage qui a réellement changé la ligne.
 */
@Service
public class EvaluationStatutService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationStatutService.class);

    private final EvaluationRepository evaluationRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final TransactionTemplate transactionParEvaluation;

    public EvaluationStatutService(EvaluationRepository evaluationRepository,
                                   NotificationService notificationService,
                                   Clock clock,
                                   PlatformTransactionManager transactionManager) {
        this.evaluationRepository = evaluationRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.transactionParEvaluation = new TransactionTemplate(transactionManager);
        this.transactionParEvaluation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return le nombre d'évaluations dont le statut a changé */
    public int mettreAJourStatuts() {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        int modifiees = 0;

        // Les fermetures d'abord : une publiée déjà fermée ne doit pas passer par EN_COURS
        modifiees += transiter(
                evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.PUBLIEE, maintenant),
                StatutEvaluation.PUBLIEE, StatutEvaluation.TERMINEE, false);
        modifiees += transiter(
                evaluationRepository.findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation.PUBLIEE, maintenant),
                StatutEvaluation.PUBLIEE, StatutEvaluation.EN_COURS, true);
        modifiees += transiter(
                evaluationRepository.findByStatutAndDateFermetureLessThanEqual(StatutEvaluation.EN_COURS, maintenant),
                StatutEvaluation.EN_COURS, StatutEvaluation.TERMINEE, false);
        return modifiees;
    }

    private int transiter(List<Evaluation> candidates, StatutEvaluation attendu, StatutEvaluation nouveau,
                          boolean notifierOuverture) {
        int modifiees = 0;
        for (Long id : candidates.stream().map(Evaluation::getId).toList()) {
            try {
                Boolean changee = transactionParEvaluation.execute(statut -> {
                    if (evaluationRepository.changerStatutSi(id, attendu, nouveau) == 0) {
                        return false; // déjà traitée ailleurs, ou dépubliée entre-temps : pas de notification
                    }
                    log.info("[Statut] Évaluation ID={} : {} -> {}", id, attendu, nouveau);
                    if (notifierOuverture) {
                        // Relue : l'entité chargée avant la mise à jour est périmée (clearAutomatically)
                        evaluationRepository.findById(id).ifPresent(notificationService::evaluationOuverte);
                    }
                    return true;
                });
                if (Boolean.TRUE.equals(changee)) {
                    modifiees++;
                }
            } catch (Exception e) {
                // Transaction annulée (statut et notifications ensemble) : reprise au prochain passage
                log.error("[Statut] Échec du passage {} -> {} de l'évaluation ID={}", attendu, nouveau, id, e);
            }
        }
        return modifiees;
    }
}
