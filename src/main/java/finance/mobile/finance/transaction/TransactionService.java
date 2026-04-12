package finance.mobile.finance.transaction;

import finance.mobile.finance.auth.AuthService;
import finance.mobile.finance.notification.NotificationService;
import finance.mobile.finance.transaction.dto.CreateTransactionRequest;
import finance.mobile.finance.transaction.dto.TransactionResponse;
import finance.mobile.finance.user.UserAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionService {

    private final AuthService authService;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    public TransactionService(
        AuthService authService,
        TransactionRepository transactionRepository,
        NotificationService notificationService
    ) {
        this.authService = authService;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions() {
        UserAccount user = authService.getCurrentUserEntity();
        return transactionRepository
            .findByUserOrderByTransactionDateDescCreatedAtDesc(user)
            .stream()
            .map(TransactionResponse::from)
            .toList();
    }

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        UserAccount user = authService.getCurrentUserEntity();
        FinanceTransaction transaction = new FinanceTransaction();

        transaction.setUser(user);
        transaction.setTitle(normalizeTitle(request.title()));
        transaction.setType(parseType(request.type()));
        transaction.setAmount(normalizeAmount(request.amount()));
        transaction.setCategory(normalizeCategory(request.category(), transaction.getType()));
        transaction.setPaymentMethod(normalizePaymentMethod(request.paymentMethod()));
        transaction.setTransactionDate(request.transactionDate() == null ? LocalDate.now() : request.transactionDate());

        FinanceTransaction savedTransaction = transactionRepository.save(transaction);
        if (savedTransaction.getType() == TransactionType.EXPENSE) {
            notificationService.createLargeExpenseNotification(user, savedTransaction);
        }

        return TransactionResponse.from(savedTransaction);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        UserAccount user = authService.getCurrentUserEntity();
        FinanceTransaction transaction = transactionRepository
            .findByIdAndUser(id, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found."));

        transactionRepository.delete(transaction);
    }

    private String normalizeTitle(String title) {
        String normalized = String.valueOf(title).trim();

        if (normalized.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must be at least 2 characters.");
        }

        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0.");
        }

        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String normalizeCategory(String category, TransactionType type) {
        String normalized = String.valueOf(category).trim();

        if (normalized.isEmpty()) {
            return type == TransactionType.INCOME ? "Income" : "Other";
        }

        return normalized;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String normalized = String.valueOf(paymentMethod).trim();

        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required.");
        }

        return normalized;
    }

    private TransactionType parseType(String rawType) {
        String normalized = String.valueOf(rawType).trim().toUpperCase();

        try {
            return TransactionType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction type must be income or expense.");
        }
    }
}
