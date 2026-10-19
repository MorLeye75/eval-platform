package sn.ept.eval.service;

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

    private final CorrectionService correctionService;

    public CorrectionDeclencheur(CorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    public void apresCommit(Long soumissionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    correctionService.corrigerSoumissionAsync(soumissionId);
                }
            });
        } else {
            correctionService.corrigerSoumissionAsync(soumissionId);
        }
    }
}
