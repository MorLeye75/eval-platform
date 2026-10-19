package sn.ept.eval.security.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import sn.ept.eval.exception.TropDeTentativesException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection contre la force brute sur /auth/login : après N échecs pour un couple (email, IP)
 * dans la fenêtre d'observation, ce couple est bloqué pendant une durée donnée.
 * Compteurs en mémoire : suffisant pour une instance unique (à remplacer par un cache partagé
 * si l'application est un jour déployée sur plusieurs instances).
 */
@Service
public class TentativesConnexionService {

    private static final Logger log = LoggerFactory.getLogger(TentativesConnexionService.class);

    private final Clock clock;
    private final int maxEchecs;
    private final Duration fenetre;
    private final Duration dureeBlocage;
    private final Map<String, Tentatives> tentatives = new ConcurrentHashMap<>();

    public TentativesConnexionService(Clock clock,
                                      @Value("${securite.login.max-echecs:5}") int maxEchecs,
                                      @Value("${securite.login.fenetre-minutes:15}") long fenetreMinutes,
                                      @Value("${securite.login.duree-blocage-minutes:15}") long dureeBlocageMinutes) {
        this.clock = clock;
        this.maxEchecs = maxEchecs;
        this.fenetre = Duration.ofMinutes(fenetreMinutes);
        this.dureeBlocage = Duration.ofMinutes(dureeBlocageMinutes);
    }

    public static String cle(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)) + "|" + ip;
    }

    /** Lève TropDeTentativesException si le couple est actuellement bloqué. */
    public void verifierNonBloque(String cle) {
        Tentatives t = tentatives.get(cle);
        if (t == null || t.bloqueJusqua == null) {
            return;
        }
        Instant maintenant = clock.instant();
        if (maintenant.isBefore(t.bloqueJusqua)) {
            long secondes = Math.max(1, Duration.between(maintenant, t.bloqueJusqua).toSeconds());
            throw new TropDeTentativesException(
                    "Trop de tentatives de connexion échouées. Réessayez dans " + ((secondes + 59) / 60) + " minute(s).",
                    secondes);
        }
    }

    public void enregistrerEchec(String cle) {
        Instant maintenant = clock.instant();
        tentatives.compute(cle, (k, t) -> {
            // Nouvelle fenêtre si aucune trace, si la fenêtre précédente est close ou si un blocage vient d'expirer
            if (t == null || maintenant.isAfter(t.debutFenetre.plus(fenetre))
                    || (t.bloqueJusqua != null && !maintenant.isBefore(t.bloqueJusqua))) {
                t = new Tentatives(maintenant);
            }
            t.echecs++;
            if (t.echecs >= maxEchecs && t.bloqueJusqua == null) {
                t.bloqueJusqua = maintenant.plus(dureeBlocage);
                log.warn("Connexion bloquée {} min après {} échecs pour {}", dureeBlocage.toMinutes(), t.echecs, k);
            }
            return t;
        });
    }

    public void reinitialiser(String cle) {
        tentatives.remove(cle);
    }

    /** Purge des entrées expirées pour borner la mémoire. */
    @Scheduled(fixedDelay = 600_000)
    public void purger() {
        Instant maintenant = clock.instant();
        tentatives.entrySet().removeIf(e -> {
            Tentatives t = e.getValue();
            Instant expiration = t.bloqueJusqua != null ? t.bloqueJusqua : t.debutFenetre.plus(fenetre);
            return maintenant.isAfter(expiration);
        });
    }

    private static final class Tentatives {
        private final Instant debutFenetre;
        private int echecs;
        private Instant bloqueJusqua;

        private Tentatives(Instant debutFenetre) {
            this.debutFenetre = debutFenetre;
        }
    }
}
