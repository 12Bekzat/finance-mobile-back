package finance.mobile.finance.card;

import finance.mobile.finance.user.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<PaymentCard, Long> {

    List<PaymentCard> findByUserOrderByCreatedAtDesc(UserAccount user);

    Optional<PaymentCard> findByIdAndUser(Long id, UserAccount user);
}
