package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Notification;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.TypeNotification;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.NotificationRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Notifications internes enregistrées en base (DS-15), déclenchées par le module enseignant (ouverture d'une
 * évaluation, publication des résultats) et par la correction (copie notée après la publication).
 *
 * <p>{@link Propagation#MANDATORY} : appelées dans la transaction de l'événement, jamais seules. Si l'événement
 * est annulé, ses notifications le sont aussi ; un appel hors transaction échoue aussitôt au lieu de créer des
 * notifications qui survivraient à un événement annulé.
 *
 * <p>Les messages ne contiennent ni note ni donnée qui pourrait changer : la page ouverte par la notification
 * lit l'état à jour.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationServiceEnBase implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceEnBase.class);

    /** « 3 déc. 2026 à 18h00 » */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH'h'mm", Locale.FRENCH);

    private final NotificationRepository notificationRepository;
    private final EtudiantRepository etudiantRepository;
    private final SoumissionRepository soumissionRepository;
    private final Clock clock;

    public NotificationServiceEnBase(NotificationRepository notificationRepository,
                                     EtudiantRepository etudiantRepository,
                                     SoumissionRepository soumissionRepository,
                                     Clock clock) {
        this.notificationRepository = notificationRepository;
        this.etudiantRepository = etudiantRepository;
        this.soumissionRepository = soumissionRepository;
        this.clock = clock;
    }

    /** Une notification par étudiant des promotions de l'évaluation, plus une pour son enseignant. */
    @Override
    public void evaluationOuverte(Evaluation evaluation) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        String jusquA = evaluation.getDateFermeture() == null ? "" : " jusqu'au " + DATE.format(evaluation.getDateFermeture());
        List<Long> promotions = evaluation.getPromotions().stream().map(Promotion::getId).toList();

        List<Notification> notifications = new ArrayList<>();
        if (!promotions.isEmpty()) {
            String message = "L'évaluation « " + evaluation.getTitre() + " » est ouverte" + jusquA + ".";
            for (Etudiant etudiant : etudiantRepository.findByPromotionIdIn(promotions)) {
                notifications.add(nouvelle(etudiant, TypeNotification.EVALUATION_OUVERTE, message, evaluation, null, maintenant));
            }
        }
        if (evaluation.getEnseignant() != null) {
            notifications.add(nouvelle(evaluation.getEnseignant(), TypeNotification.EVALUATION_OUVERTE,
                    "Votre évaluation « " + evaluation.getTitre() + " » est ouverte aux étudiants" + jusquA + ".",
                    evaluation, null, maintenant));
        }
        notificationRepository.saveAll(notifications);
        log.info("[Notification] Évaluation ID={} ouverte : {} notification(s)", evaluation.getId(), notifications.size());
    }

    /**
     * Une notification par copie déjà notée. Les copies encore en correction sont prévenues plus tard, une par une,
     * par {@link #resultatPublie} quand elles sont notées.
     */
    @Override
    public void resultatsPublies(Evaluation evaluation) {
        List<Soumission> notees = soumissionRepository.findByEvaluationIdAndStatut(evaluation.getId(), StatutSoumission.EVALUE);
        if (notees.isEmpty()) return;
        Set<Long> deja = notificationRepository.soumissionsDejaNotifiees(TypeNotification.RESULTAT_PUBLIE,
                notees.stream().map(Soumission::getId).toList());
        LocalDateTime maintenant = LocalDateTime.now(clock);
        List<Notification> notifications = notees.stream()
                .filter(c -> !deja.contains(c.getId()))
                .map(c -> nouvelle(c.getEtudiant(), TypeNotification.RESULTAT_PUBLIE, messageResultat(evaluation),
                        evaluation, c, maintenant))
                .toList();
        notificationRepository.saveAll(notifications);
        log.info("[Notification] Résultats de l'évaluation ID={} publiés : {} notification(s)",
                evaluation.getId(), notifications.size());
    }

    /**
     * Copie notée après la publication des résultats. Rien si les résultats ne sont pas publiés ou si la copie
     * n'est pas notée (aucune note n'est annoncée avant, DS-13), ni si elle a déjà sa notification : la correction
     * automatique et la validation de l'enseignant (DS-08) peuvent toutes deux la signaler.
     */
    @Override
    public void resultatPublie(Soumission copie) {
        Evaluation evaluation = copie.getEvaluation();
        if (copie.getStatut() != StatutSoumission.EVALUE || !Boolean.TRUE.equals(evaluation.getResultatsPublies())) {
            log.warn("[Notification] Copie ID={} non notifiée : statut {}, résultats publiés {}",
                    copie.getId(), copie.getStatut(), evaluation.getResultatsPublies());
            return;
        }
        if (!notificationRepository.soumissionsDejaNotifiees(TypeNotification.RESULTAT_PUBLIE, List.of(copie.getId())).isEmpty()) {
            return;
        }
        notificationRepository.save(nouvelle(copie.getEtudiant(), TypeNotification.RESULTAT_PUBLIE,
                messageResultat(evaluation), evaluation, copie, LocalDateTime.now(clock)));
        log.info("[Notification] Résultat publié pour la copie ID={}", copie.getId());
    }

    private static String messageResultat(Evaluation evaluation) {
        return "Les résultats de « " + evaluation.getTitre() + " » sont publiés.";
    }

    private static Notification nouvelle(Utilisateur destinataire, TypeNotification type, String message,
                                         Evaluation evaluation, Soumission soumission, LocalDateTime dateEnvoi) {
        Notification n = new Notification();
        n.setDestinataire(destinataire);
        n.setType(type);
        n.setMessage(message);
        n.setEvaluation(evaluation);
        n.setSoumission(soumission);
        n.setDateEnvoi(dateEnvoi);
        n.setLue(false);
        return n;
    }
}
