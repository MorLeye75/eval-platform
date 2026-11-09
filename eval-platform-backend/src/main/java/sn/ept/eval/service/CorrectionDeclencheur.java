package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.ept.eval.repository.SoumissionRepository;

/**
 * Lance la correction asynchrone d'une copie une fois la transaction courante validée
 * (sinon le thread de correction pourrait lire la copie avant son passage à SOUMIS).
 * Composant distinct de CorrectionService : un appel interne à la méthode @Async contournerait
 * le proxy Spring et s'exécuterait de façon synchrone.
 *
 * Deux files (DS-12) : les évaluations avec du code passent par l'exécuteur limité aux workers Judge0,
 * les autres (QCM, quiz) par l'exécuteur rapide, pour ne jamais attendre derrière Judge0.
 */
@Component
public class CorrectionDeclencheur {

    private static final Logger log = LoggerFactory.getLogger(CorrectionDeclencheur.class);

    private final CorrectionService correctionService;
    private final CorrectionsEnFile enFile;
    private final SoumissionRepository soumissionRepository;

    public CorrectionDeclencheur(CorrectionService correctionService, CorrectionsEnFile enFile,
                                 SoumissionRepository soumissionRepository) {
        this.correctionService = correctionService;
        this.enFile = enFile;
        this.soumissionRepository = soumissionRepository;
    }

    public void apresCommit(Long soumissionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lancer(soumissionId);
                }
            });
        } else {
            lancer(soumissionId);
        }
    }

    /**
     * La copie est déjà remise (commit fait) : aucune erreur ne doit remonter vers la requête. Une copie
     * non mise en file reste SOUMIS et sera corrigée par la relance planifiée.
     */
    private void lancer(Long soumissionId) {
        if (!enFile.ajouter(soumissionId)) {
            log.debug("Correction de la soumission ID={} déjà en file", soumissionId);
            return;
        }
        try {
            if (soumissionRepository.evaluationContientCode(soumissionId)) {
                correctionService.corrigerSoumissionAvecCodeAsync(soumissionId);
            } else {
                correctionService.corrigerSoumissionAsync(soumissionId);
            }
        } catch (TaskRejectedException e) {
            // Le finally de la correction ne s'exécutera jamais : sans ce retrait, la relance l'ignorerait
            enFile.retirer(soumissionId);
            log.warn("File des corrections saturée : correction de la soumission ID={} reportée à la relance planifiée",
                    soumissionId);
        } catch (RuntimeException e) {
            enFile.retirer(soumissionId);
            log.warn("Correction de la soumission ID={} non lancée, reportée à la relance planifiée", soumissionId, e);
        }
    }
}
