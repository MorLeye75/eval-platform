package sn.ept.eval.security.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.JetonRevoque;
import sn.ept.eval.repository.JetonRevoqueRepository;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Révocation des jetons à la déconnexion (DS-14). Seule la session fermée est révoquée : se déconnecter sur son
 * téléphone ne coupe pas l'épreuve en cours sur le PC. Les révocations sont en base (elles survivent à un
 * redémarrage) et purgées une fois le jeton expiré.
 */
@Service
public class RevocationJetonsService {

    private static final Logger log = LoggerFactory.getLogger(RevocationJetonsService.class);

    private final JetonRevoqueRepository repository;
    private final Clock clock;

    public RevocationJetonsService(JetonRevoqueRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Révoque le jeton jusqu'à son expiration. Sans effet s'il l'est déjà. */
    @Transactional
    public void revoquer(String jti, LocalDateTime expiration) {
        if (jti == null || repository.existsById(jti)) return;
        repository.save(new JetonRevoque(jti, expiration, LocalDateTime.now(clock)));
    }

    /** Lu à chaque requête authentifiée : une recherche par clé primaire. */
    @Transactional(readOnly = true)
    public boolean estRevoque(String jti) {
        return jti != null && repository.existsById(jti);
    }

    /** Toutes les heures : les jetons expirés seraient refusés de toute façon. */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purger() {
        int supprimes = repository.supprimerExpires(LocalDateTime.now(clock));
        if (supprimes > 0) log.info("{} révocation(s) de jetons expirés purgée(s)", supprimes);
    }
}
