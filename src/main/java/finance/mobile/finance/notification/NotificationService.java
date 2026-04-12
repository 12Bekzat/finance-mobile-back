package finance.mobile.finance.notification;

import finance.mobile.finance.auth.AuthService;
import finance.mobile.finance.card.PaymentCard;
import finance.mobile.finance.goal.Goal;
import finance.mobile.finance.notification.dto.NotificationResponse;
import finance.mobile.finance.notification.dto.NotificationsEnvelope;
import finance.mobile.finance.transaction.FinanceTransaction;
import finance.mobile.finance.user.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private static final BigDecimal LARGE_EXPENSE_THRESHOLD = new BigDecimal("500.00");

    private final AuthService authService;
    private final NotificationRepository notificationRepository;

    public NotificationService(AuthService authService, NotificationRepository notificationRepository) {
        this.authService = authService;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationsEnvelope getNotifications() {
        UserAccount user = authService.getCurrentUserEntity();
        List<NotificationResponse> notifications = notificationRepository
            .findByUserOrderByCreatedAtDesc(user)
            .stream()
            .map(NotificationResponse::from)
            .toList();

        long unreadCount = notifications.stream().filter(notification -> !notification.read()).count();
        return new NotificationsEnvelope(notifications, unreadCount);
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        UserAccount user = authService.getCurrentUserEntity();
        UserNotification notification = notificationRepository
            .findByIdAndUser(id, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
        }

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationsEnvelope markAllRead() {
        UserAccount user = authService.getCurrentUserEntity();
        List<UserNotification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        Instant now = Instant.now();

        notifications.stream().filter(notification -> !notification.isRead()).forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(now);
        });

        notificationRepository.saveAll(notifications);
        return getNotifications();
    }

    @Transactional
    public NotificationResponse createCardLinkedNotification(UserAccount user, PaymentCard card) {
        return createNotification(
            user,
            NotificationType.CARD_LINKED,
            "Card linked",
            card.getBrand() + " ending in " + card.getLast4() + " is now available for payments.",
            "/(tabs)/cards"
        );
    }

    @Transactional
    public NotificationResponse createGoalCreatedNotification(UserAccount user, Goal goal) {
        return createNotification(
            user,
            NotificationType.GOAL_CREATED,
            "Goal created",
            "Goal \"" + goal.getTitle() + "\" has been added to your plan.",
            "/(tabs)/goals"
        );
    }

    @Transactional
    public NotificationResponse createGoalCompletedNotification(UserAccount user, Goal goal) {
        return createNotification(
            user,
            NotificationType.GOAL_COMPLETED,
            "Goal completed",
            "You reached your goal \"" + goal.getTitle() + "\".",
            "/(tabs)/goals"
        );
    }

    @Transactional
    public NotificationResponse createLargeExpenseNotification(UserAccount user, FinanceTransaction transaction) {
        if (transaction.getAmount().compareTo(LARGE_EXPENSE_THRESHOLD) < 0) {
            return null;
        }

        return createNotification(
            user,
            NotificationType.LARGE_EXPENSE,
            "Large expense recorded",
            "An expense of $" + transaction.getAmount() + " was recorded for " + transaction.getTitle() + ".",
            "/(tabs)/list"
        );
    }

    @Transactional
    public NotificationResponse createNotification(
        UserAccount user,
        NotificationType type,
        String title,
        String message,
        String targetPath
    ) {
        UserNotification notification = new UserNotification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetPath(targetPath);
        notification.setRead(false);

        return NotificationResponse.from(notificationRepository.save(notification));
    }
}
