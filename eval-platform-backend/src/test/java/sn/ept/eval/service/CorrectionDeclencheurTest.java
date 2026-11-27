package sn.ept.eval.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.ept.eval.repository.SoumissionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Mise en file des corrections (DS-12) : exécuteur choisi selon la présence de code, une copie déjà en file
 * n'y est pas remise, et une tâche refusée (file pleine) ne fait ni échouer la remise ni bloquer la relance.
 */
class CorrectionDeclencheurTest {

    private final CorrectionService correctionService = mock(CorrectionService.class);
    private final SoumissionRepository soumissionRepository = mock(SoumissionRepository.class);
    private final CorrectionsEnFile enFile = new CorrectionsEnFile();
    private final CorrectionDeclencheur declencheur = new CorrectionDeclencheur(correctionService, enFile, soumissionRepository);

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
    @DisplayName("File pleine : la copie est retirée des copies en file, la relance suivante la remet en file")
    void filePleine_RetireeDuRegistre() {
        when(correctionService.corrigerSoumissionAsync(100L))
                .thenThrow(new TaskRejectedException("file pleine"))
                .thenReturn(null);

        assertThatCode(() -> declencheur.apresCommit(100L)).doesNotThrowAnyException();
        assertThat(enFile.contient(100L)).isFalse();

        declencheur.apresCommit(100L); // relance planifiée
        verify(correctionService, times(2)).corrigerSoumissionAsync(100L);
        assertThat(enFile.contient(100L)).isTrue();
    }

    @Test
    @DisplayName("Copie déjà en file : la relance ne l'y remet pas")
    void dejaEnFile_Ignoree() {
        declencheur.apresCommit(100L);
        declencheur.apresCommit(100L);

        verify(correctionService, times(1)).corrigerSoumissionAsync(100L);
    }

    @Test
    @DisplayName("Évaluation avec du code : exécuteur limité à Judge0 ; sans code : exécuteur rapide")
    void choixDeLExecuteur() {
        when(soumissionRepository.evaluationContientCode(100L)).thenReturn(true);
        when(soumissionRepository.evaluationContientCode(200L)).thenReturn(false);

        declencheur.apresCommit(100L);
        declencheur.apresCommit(200L);

        verify(correctionService).corrigerSoumissionAvecCodeAsync(100L);
        verify(correctionService, never()).corrigerSoumissionAsync(100L);
        verify(correctionService).corrigerSoumissionAsync(200L);
        verify(correctionService, never()).corrigerSoumissionAvecCodeAsync(200L);
    }

    @Test
    @DisplayName("Base indisponible pour choisir l'exécuteur : aucune exception, copie retirée pour la relance")
    void erreurAvantMiseEnFile_SansErreur() {
        when(soumissionRepository.evaluationContientCode(100L)).thenThrow(new DataAccessResourceFailureException("base"));

        assertThatCode(() -> declencheur.apresCommit(100L)).doesNotThrowAnyException();
        assertThat(enFile.contient(100L)).isFalse();
        verifyNoInteractions(correctionService);
    }
}
