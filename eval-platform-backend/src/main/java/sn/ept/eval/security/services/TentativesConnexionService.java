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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection contre la force brute sur /auth/login, à deux niveaux :
 * - couple (email, IP) : après N échecs dans la fenêtre d'observation, ce couple est bloqué ;
 * - IP seule, tous comptes confondus (password spraying : un mot de passe courant essayé sur beaucoup de
 *   comptes). Seuil volontairement haut : les étudiants d'un campus sortent souvent par la même IP (NAT).
 *   Une connexion réussie ne remet pas ce compteur à zéro (l'attaquant pourrait se connecter à son compte).
 *
 * Compteurs en mémoire, bornés en nombre d'entrées (des emails inventés ne doivent pas épuiser la mémoire) :
 * suffisant pour une instance unique (à remplacer par un cache partagé en cas de déploiement multi-instances).
 * L'IP est celle vue par le serveur : derrière un reverse proxy, activer FORWARD_HEADERS_STRATEGY=native.
 */
@Service
public class TentativesConnexionService {

    private static final Logger log = LoggerFactory.getLogger(TentativesConnexionService.class);

    private final Clock clock;
    private final Duration fenetre;
    private final Duration dureeBlocage;
    private final Compteurs parCouple;
    private final Compteurs parIp;

    public TentativesConnexionService(Clock clock,
                                      @Value("${securite.login.max-echecs:5}") int maxEchecs,
                                      @Value("${securite.login.fenetre-minutes:15}") long fenetreMinutes,
                                      @Value("${securite.login.duree-blocage-minutes:15}") long dureeBlocageMinutes,
                                      @Value("${securite.login.max-echecs-par-ip:100}") int maxEchecsParIp,
                                      @Value("${securite.login.max-compteurs:10000}") int maxCompteurs) {
        this.clock = clock;
        this.fenetre = Duration.ofMinutes(fenetreMinutes);
        this.dureeBlocage = Duration.ofMinutes(dureeBlocageMinutes);
        this.parCouple = new Compteurs("couple", maxEchecs, maxCompteurs,
                "Trop de tentatives de connexion échouées. Réessayez dans %d minute(s).");
        this.parIp = new Compteurs("IP", maxEchecsParIp, maxCompteurs,
                "Trop de tentatives de connexion échouées depuis votre réseau. Réessayez dans %d minute(s).");
    }

    private static String couple(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)) + "|" + ip;
    }

    /** Lève TropDeTentativesException si l'IP ou le couple (email, IP) est actuellement bloqué. */
    public void verifierNonBloque(String email, String ip) {
        Instant maintenant = clock.instant();
        parIp.verifier(ip, maintenant);
        parCouple.verifier(couple(email, ip), maintenant);
    }

    public void enregistrerEchec(String email, String ip) {
        Instant maintenant = clock.instant();
        parCouple.echec(couple(email, ip), maintenant);
        parIp.echec(ip, maintenant);
    }

    /** Connexion réussie : seul le compteur du couple repart de zéro. */
    public void reinitialiser(String email, String ip) {
        parCouple.retirer(couple(email, ip));
    }

    /** Purge des entrées expirées. */
    @Scheduled(fixedDelay = 600_000)
    public void purger() {
        Instant maintenant = clock.instant();
        parCouple.purger(maintenant);
        parIp.purger(maintenant);
    }

    /** Nombre total d'entrées en mémoire (couples et IP). */
    public int nombreDeCompteurs() {
        return parCouple.taille() + parIp.taille();
    }

    /** Compteurs d'échecs pour un type de clé, avec seuil de blocage et nombre d'entrées borné. */
    private final class Compteurs {
        private final String nom;
        private final int seuil;
        private final int maxEntrees;
        private final String message;
        private final Map<String, Tentatives> tentatives = new ConcurrentHashMap<>();

        private Compteurs(String nom, int seuil, int maxEntrees, String message) {
            this.nom = nom;
            this.seuil = seuil;
            this.maxEntrees = maxEntrees;
            this.message = message;
        }

        void verifier(String cle, Instant maintenant) {
            Tentatives t = tentatives.get(cle);
            if (t == null || t.bloqueJusqua == null || !maintenant.isBefore(t.bloqueJusqua)) {
                return;
            }
            long secondes = Math.max(1, Duration.between(maintenant, t.bloqueJusqua).toSeconds());
            throw new TropDeTentativesException(String.format(message, (secondes + 59) / 60), secondes);
        }

        void echec(String cle, Instant maintenant) {
            if (!tentatives.containsKey(cle) && tentatives.size() >= maxEntrees) {
                faireDeLaPlace(maintenant);
            }
            tentatives.compute(cle, (k, t) -> {
                // Nouvelle fenêtre si aucune trace, si la fenêtre précédente est close ou si un blocage vient d'expirer
                if (t == null || maintenant.isAfter(t.debutFenetre.plus(fenetre))
                        || (t.bloqueJusqua != null && !maintenant.isBefore(t.bloqueJusqua))) {
                    t = new Tentatives(maintenant);
                }
                t.echecs++;
                if (t.echecs >= seuil && t.bloqueJusqua == null) {
                    t.bloqueJusqua = maintenant.plus(dureeBlocage);
                    log.warn("Connexion bloquée {} min après {} échecs ({}) pour {}", dureeBlocage.toMinutes(), t.echecs, nom, k);
                }
                return t;
            });
        }

        void retirer(String cle) {
            tentatives.remove(cle);
        }

        int taille() {
            return tentatives.size();
        }

        void purger(Instant maintenant) {
            tentatives.entrySet().removeIf(e -> {
                Tentatives t = e.getValue();
                Instant expiration = t.bloqueJusqua != null ? t.bloqueJusqua : t.debutFenetre.plus(fenetre);
                return maintenant.isAfter(expiration);
            });
        }

        /**
         * Table pleine : purge des entrées expirées, puis éviction d'un dixième des entrées, les plus anciennes
         * non bloquées d'abord (un blocage en cours n'est évincé qu'en dernier recours, le plus proche de sa fin).
         */
        private void faireDeLaPlace(Instant maintenant) {
            purger(maintenant);
            if (tentatives.size() < maxEntrees) return;
            int aEvincer = Math.max(1, maxEntrees / 10);
            List<Map.Entry<String, Tentatives>> candidats = new ArrayList<>(tentatives.entrySet());
            candidats.sort(Comparator
                    .comparing((Map.Entry<String, Tentatives> e) -> e.getValue().bloqueJusqua != null)
                    .thenComparing(e -> e.getValue().bloqueJusqua != null ? e.getValue().bloqueJusqua : e.getValue().debutFenetre));
            candidats.stream().limit(aEvincer).forEach(e -> tentatives.remove(e.getKey(), e.getValue()));
            log.warn("Compteurs de connexion ({}) saturés : {} entrées les plus anciennes évincées", nom, aEvincer);
        }
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
