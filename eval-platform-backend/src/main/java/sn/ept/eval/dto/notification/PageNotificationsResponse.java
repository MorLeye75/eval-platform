package sn.ept.eval.dto.notification;

import java.util.List;

/** Une page de notifications, les plus récentes d'abord, et le nombre total pour savoir s'il en reste. */
public record PageNotificationsResponse(List<NotificationResponse> notifications, int page, int taille, long total) {
}
