package sn.ept.eval.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Exécution asynchrone d'une proposition IA (DS-08, section B), sur l'exécuteur des corrections sans code. */
@Component
public class CorrectionIATache {

    private final CorrectionIAService service;

    public CorrectionIATache(CorrectionIAService service) {
        this.service = service;
    }

    @Async("correctionTaskExecutor")
    public void proposerAsync(Long reponseId) {
        service.proposer(reponseId);
    }
}
