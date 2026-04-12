package finance.mobile.finance.transaction;

import finance.mobile.finance.user.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<FinanceTransaction, Long> {

    List<FinanceTransaction> findByUserOrderByTransactionDateDescCreatedAtDesc(UserAccount user);

    Optional<FinanceTransaction> findByIdAndUser(Long id, UserAccount user);
}
