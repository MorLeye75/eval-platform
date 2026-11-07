package sn.ept.eval.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * File des corrections saturée (fin d'examen d'une promotion entière) : la tâche refusée ne doit pas faire
 * échouer la remise de la copie, déjà validée ; la relance planifiée corrigera la copie restée SOUMIS.
 */
class CorrectionDeclencheurTest {

    private final CorrectionService correctionService = mock(CorrectionService.class);
    private final CorrectionDeclencheur declencheur = new CorrectionDeclencheur(correctionService);

    @AfterEach
    void nettoyer() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Après le commit d'une remise : file pleine, aucune exception ne remonte vers la requête HTTP")
    void filePleine_ApresCommit_SansErreur() {
        when(correctionService.corrigerSoumissionAsync(100L)).thenThrow(new TaskRejectedException("file pleine"));
        TransactionSynchronizationManager.initSynchronization();

        declencheur.apresCommit(100L);

        // Ce que fait le gestionnaire de transactions juste après le commit
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verify(correctionService).corrigerSoumissionAsync(100L);
    }

    @Test
    @DisplayName("Hors transaction (relance planifiée) : file pleine, aucune exception")
    void filePleine_HorsTransaction_SansErreur() {
        when(correctionService.corrigerSoumissionAsync(100L)).thenThrow(new TaskRejectedException("file pleine"));

        assertThatCode(() -> declencheur.apresCommit(100L)).doesNotThrowAnyException();
    }
}
