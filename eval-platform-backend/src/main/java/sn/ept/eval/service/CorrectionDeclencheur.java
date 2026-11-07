package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lance la correction asynchrone d'une copie une fois la transaction courante validée
 * (sinon le thread de correction pourrait lire la copie avant son passage à SOUMIS).
 * Composant distinct de CorrectionService : un appel interne à la méthode @Async contournerait
 * le proxy Spring et s'exécuterait de façon synchrone.
 */
@Component
public class CorrectionDeclencheur {

    private static final Logger log = LoggerFactory.getLogger(CorrectionDeclencheur.class);

    private final CorrectionService correctionService;

    public CorrectionDeclencheur(CorrectionService correctionService) {
        this.correctionService = correctionService;
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
     * File des corrections saturée : la copie est déjà remise (commit fait), la requête ne doit pas échouer.
     * La copie reste SOUMIS et sera corrigée par la relance planifiée.
     */
    private void lancer(Long soumissionId) {
        try {
            correctionService.corrigerSoumissionAsync(soumissionId);
        } catch (TaskRejectedException e) {
            log.warn("File des corrections saturée : correction de la soumission ID={} reportée à la relance planifiée",
                    soumissionId);
        }
    }
}
