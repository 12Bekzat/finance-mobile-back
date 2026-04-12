package finance.mobile.finance.notification.dto;

import java.util.List;

public record NotificationsEnvelope(List<NotificationResponse> notifications, long unreadCount) {}
