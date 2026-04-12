package finance.mobile.finance.notification;

import finance.mobile.finance.user.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findByUserOrderByCreatedAtDesc(UserAccount user);

    Optional<UserNotification> findByIdAndUser(Long id, UserAccount user);
}
