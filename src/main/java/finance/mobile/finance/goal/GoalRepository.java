package finance.mobile.finance.goal;

import finance.mobile.finance.user.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserOrderByCreatedAtDesc(UserAccount user);

    Optional<Goal> findByIdAndUser(Long id, UserAccount user);
}
