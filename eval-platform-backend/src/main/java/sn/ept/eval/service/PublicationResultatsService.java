package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.ProgrammationPublicationResponse;
import sn.ept.eval.dto.evaluation.ResultatsPublicationResponse;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Publication des résultats d'une évaluation par son enseignant (DS-07) : l'autre moitié de « publication et
 * consultation des résultats », dont l'étudiant voit la suite en DS-13 (une copie n'est consultable que si elle est
 * notée ET que {@code resultatsPublies} est vrai).
 *
 * <p>Conditions : évaluation terminée et marge de tolérance écoulée (plus personne ne compose, donc les bonnes
 * réponses peuvent être dévoilées), résultats pas déjà publiés. La bascule est une mise à jour conditionnelle :
 * deux clics simultanés ne notifient pas deux fois.
 */
@Service
@Transactional
public class PublicationResultatsService {

    private final EvaluationService evaluationService;
    private final EvaluationRepository evaluationRepository;
    private final SoumissionRepository soumissionRepository;
    private final NotificationService notificationService;
    private final TempsEpreuveService tempsEpreuveService;
    /** Une transaction par évaluation échue : l'échec de l'une n'annule pas la publication des autres. */
    private final TransactionTemplate transaction;

    private static final Logger log = LoggerFactory.getLogger(PublicationResultatsService.class);

    public PublicationResultatsService(EvaluationService evaluationService,
                                       EvaluationRepository evaluationRepository,
                                       SoumissionRepository soumissionRepository,
                                       NotificationService notificationService,
                                       TempsEpreuveService tempsEpreuveService,
                                       PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.evaluationService = evaluationService;
        this.evaluationRepository = evaluationRepository;
        this.soumissionRepository = soumissionRepository;
        this.notificationService = notificationService;
        this.tempsEpreuveService = tempsEpreuveService;
    }

    public ResultatsPublicationResponse publier(Long evaluationId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);

        if (Boolean.TRUE.equals(evaluation.getResultatsPublies())) {
            throw dejaPublies();
        }
        if (evaluation.getStatut() != StatutEvaluation.TERMINEE) {
            throw new EvaluationNonModifiableException(
                    "Les résultats ne peuvent être publiés qu'une fois l'évaluation terminée.");
        }
        // Plus personne ne compose après la fermeture + la marge : c'est aussi la règle de DS-13 pour les corrigés
        if (tempsEpreuveService.maintenant().isBefore(finDeLaMarge(evaluation))) {
            throw new EvaluationNonModifiableException(
                    "Des étudiants peuvent encore rendre leur copie : réessayez après la marge de tolérance de "
                            + TempsEpreuveService.MARGE_TOLERANCE_MINUTES + " minutes suivant la fermeture.");
        }

        return basculer(evaluationId).orElseThrow(this::dejaPublies);
    }

    /**
     * Bascule atomique de la publication, commune au clic manuel et au planificateur : vide si un autre appel vient de
     * publier (aucune notification en double). Efface la programmation, devenue sans objet.
     */
    private java.util.Optional<ResultatsPublicationResponse> basculer(Long evaluationId) {
        if (evaluationRepository.publierResultatsSiNonPublies(evaluationId) == 0) {
            return java.util.Optional.empty();
        }
        // Relue : l'entité chargée avant la mise à jour est périmée (clearAutomatically)
        Evaluation publiee = evaluationRepository.findById(evaluationId).orElseThrow();
        publiee.setPublicationResultatsPrevueLe(null);
        evaluationRepository.save(publiee);
        notificationService.resultatsPublies(publiee);

        long total = soumissionRepository.countByEvaluationId(evaluationId);
        long notees = soumissionRepository.countByEvaluationIdAndStatut(evaluationId, StatutSoumission.EVALUE);
        return java.util.Optional.of(new ResultatsPublicationResponse(evaluationId, true, total, notees, total - notees));
    }

    private static LocalDateTime finDeLaMarge(Evaluation evaluation) {
        return evaluation.getDateFermeture().plusMinutes(TempsEpreuveService.MARGE_TOLERANCE_MINUTES);
    }

    // ------------------------------------------------------------------ publication différée (DS-07)

    public ProgrammationPublicationResponse programmer(Long evaluationId, String email, LocalDateTime date) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);
        if (Boolean.TRUE.equals(evaluation.getResultatsPublies())) {
            throw dejaPublies();
        }
        if (evaluation.getStatut() == StatutEvaluation.BROUILLON) {
            throw new EvaluationNonModifiableException("Une évaluation en brouillon n'a pas de résultats à publier.");
        }
        if (date == null) {
            throw new IllegalArgumentException("La date de publication est obligatoire.");
        }
        LocalDateTime plusTot = finDeLaMarge(evaluation);
        LocalDateTime maintenant = tempsEpreuveService.maintenant();
        if (!date.isAfter(maintenant)) {
            throw new IllegalArgumentException("La date de publication doit être dans le futur.");
        }
        if (date.isBefore(plusTot)) {
            throw new IllegalArgumentException("La date de publication doit être postérieure à la fin de la marge de tolérance ("
                    + plusTot + ") : des étudiants peuvent encore rendre leur copie avant.");
        }
        evaluation.setPublicationResultatsPrevueLe(date);
        evaluationRepository.save(evaluation);
        return new ProgrammationPublicationResponse(evaluationId, date);
    }

    public void annulerProgrammation(Long evaluationId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);
        if (evaluation.getPublicationResultatsPrevueLe() == null) {
            throw new EvaluationNonModifiableException("Aucune publication n'est programmée pour cette évaluation.");
        }
        evaluation.setPublicationResultatsPrevueLe(null);
        evaluationRepository.save(evaluation);
    }

    /**
     * Publie les évaluations dont la date programmée est échue (appelée par le planificateur). Une évaluation pas encore
     * terminée, ou dont la marge n'est pas écoulée, reste programmée et sera reprise à l'appel suivant. Retourne le
     * nombre d'évaluations publiées.
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public int publierProgrammees() {
        LocalDateTime maintenant = tempsEpreuveService.maintenant();
        List<Long> echues = transaction.execute(tx -> evaluationRepository
                .findByResultatsPubliesFalseAndPublicationResultatsPrevueLeLessThanEqual(maintenant).stream().map(Evaluation::getId).toList());
        int publiees = 0;
        for (Long id : echues == null ? List.<Long>of() : echues) {
            try {
                Boolean publiee = transaction.execute(tx -> {
                    Evaluation e = evaluationRepository.findById(id).orElse(null);
                    if (e == null || Boolean.TRUE.equals(e.getResultatsPublies())) return false;
                    if (e.getStatut() != StatutEvaluation.TERMINEE || maintenant.isBefore(finDeLaMarge(e))) return false;
                    return basculer(id).isPresent();
                });
                if (Boolean.TRUE.equals(publiee)) publiees++;
            } catch (RuntimeException e) {
                log.error("[Planifié] Publication programmée de l'évaluation ID={} en échec", id, e);
            }
        }
        return publiees;
    }

    private EvaluationNonModifiableException dejaPublies() {
        return new EvaluationNonModifiableException("Les résultats de cette évaluation sont déjà publiés.");
    }
}
