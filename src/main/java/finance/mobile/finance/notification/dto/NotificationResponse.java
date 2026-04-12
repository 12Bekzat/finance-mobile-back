package finance.mobile.finance.notification.dto;

import finance.mobile.finance.notification.UserNotification;
import java.time.Instant;

public record NotificationResponse(
    Long id,
    String type,
    String title,
    String message,
    String targetPath,
    boolean read,
    Instant readAt,
    Instant createdAt
) {
    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType().name().toLowerCase(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getTargetPath(),
            notification.isRead(),
            notification.getReadAt(),
            notification.getCreatedAt()
        );
    }
}
