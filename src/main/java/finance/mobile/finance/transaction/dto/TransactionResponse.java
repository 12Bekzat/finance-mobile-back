package finance.mobile.finance.transaction.dto;

import finance.mobile.finance.transaction.FinanceTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
    Long id,
    String title,
    String type,
    BigDecimal amount,
    String category,
    String paymentMethod,
    LocalDate transactionDate,
    Instant createdAt
) {
    public static TransactionResponse from(FinanceTransaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getTitle(),
            transaction.getType().name().toLowerCase(),
            transaction.getAmount(),
            transaction.getCategory(),
            transaction.getPaymentMethod(),
            transaction.getTransactionDate(),
            transaction.getCreatedAt()
        );
    }
}
