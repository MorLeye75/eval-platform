package sn.ept.eval.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.dto.notification.NotificationResponse;
import sn.ept.eval.model.Notification;
import sn.ept.eval.model.enums.TypeNotification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Compteur de la cloche, interrogé toutes les 60 s : index (utilisateur_id, lue). */
    long countByDestinataireIdAndLueFalse(Long utilisateurId);

    /**
     * Notifications d'un utilisateur, les plus récentes d'abord, directement en DTO (pas de chargement de
     * l'évaluation ni de la copie, seulement leurs identifiants). Index (utilisateur_id, date_envoi).
     */
    @Query(value = "select new sn.ept.eval.dto.notification.NotificationResponse("
            + "n.id, n.type, n.message, n.lue, n.dateEnvoi, e.id, s.id)"
            + " from Notification n left join n.evaluation e left join n.soumission s"
            + " where n.destinataire.id = :utilisateurId order by n.dateEnvoi desc, n.id desc",
            countQuery = "select count(n) from Notification n where n.destinataire.id = :utilisateurId")
    Page<NotificationResponse> pageDe(@Param("utilisateurId") Long utilisateurId, Pageable pageable);

    /** @return 0 si la notification n'existe pas ou appartient à un autre utilisateur */
    @Modifying
    @Query("update Notification n set n.lue = true where n.id = :id and n.destinataire.id = :utilisateurId")
    int marquerLue(@Param("id") Long id, @Param("utilisateurId") Long utilisateurId);

    @Modifying
    @Query("update Notification n set n.lue = true where n.destinataire.id = :utilisateurId and n.lue = false")
    int toutMarquerLu(@Param("utilisateurId") Long utilisateurId);

    /** Copies qui ont déjà leur notification de ce type : pas de doublon si l'événement est signalé deux fois. */
    @Query("select n.soumission.id from Notification n where n.type = :type and n.soumission.id in :soumissionIds")
    Set<Long> soumissionsDejaNotifiees(@Param("type") TypeNotification type,
                                       @Param("soumissionIds") Collection<Long> soumissionIds);

    @Modifying
    @Query("delete from Notification n where n.lue = true and n.dateEnvoi < :limite")
    int supprimerLuesAvant(@Param("limite") LocalDateTime limite);
}
