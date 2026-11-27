package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.notification.NombreNonLuesResponse;
import sn.ept.eval.dto.notification.NotificationResponse;
import sn.ept.eval.dto.notification.PageNotificationsResponse;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.repository.NotificationRepository;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Consultation de ses notifications par l'utilisateur connecté (DS-15), tous rôles. Le destinataire vient toujours
 * du jeton, jamais de la requête : une notification d'un autre utilisateur n'est ni lue ni modifiée (404).
 */
@Service
public class MesNotificationsService {

    private static final Logger log = LoggerFactory.getLogger(MesNotificationsService.class);

    static final int TAILLE_MAX = 50;
    static final int MOIS_CONSERVATION_LUES = 6;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public MesNotificationsService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /** Les plus récentes d'abord ; taille ramenée entre 1 et {@value #TAILLE_MAX}. */
    @Transactional(readOnly = true)
    public PageNotificationsResponse page(Long utilisateurId, int page, int taille) {
        int p = Math.max(0, page);
        int t = Math.min(Math.max(1, taille), TAILLE_MAX);
        Page<NotificationResponse> resultat = notificationRepository.pageDe(utilisateurId, PageRequest.of(p, t));
        return new PageNotificationsResponse(resultat.getContent(), p, t, resultat.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NombreNonLuesResponse nombreNonLues(Long utilisateurId) {
        return new NombreNonLuesResponse(notificationRepository.countByDestinataireIdAndLueFalse(utilisateurId));
    }

    /** 404 si elle n'existe pas ou appartient à un autre utilisateur : on ne révèle pas qu'elle existe. */
    @Transactional
    public void marquerLue(Long notificationId, Long utilisateurId) {
        if (notificationRepository.marquerLue(notificationId, utilisateurId) == 0) {
            throw new RessourceNonTrouveeException("Notification introuvable (ID: " + notificationId + ")");
        }
    }

    @Transactional
    public void toutMarquerLu(Long utilisateurId) {
        notificationRepository.toutMarquerLu(utilisateurId);
    }

    /** Chaque nuit à 3 h 30 : les notifications lues de plus de 6 mois. Les non lues sont gardées. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purger() {
        int supprimees = notificationRepository.supprimerLuesAvant(
                LocalDateTime.now(clock).minusMonths(MOIS_CONSERVATION_LUES));
        if (supprimees > 0) log.info("[Notification] {} notification(s) lue(s) de plus de 6 mois purgée(s)", supprimees);
    }
}
