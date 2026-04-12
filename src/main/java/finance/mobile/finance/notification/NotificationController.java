package finance.mobile.finance.notification;

import finance.mobile.finance.notification.dto.NotificationResponse;
import finance.mobile.finance.notification.dto.NotificationsEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationsEnvelope getNotifications() {
        return notificationService.getNotifications();
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PutMapping("/read-all")
    public NotificationsEnvelope markAllRead() {
        return notificationService.markAllRead();
    }
}
