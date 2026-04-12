package finance.mobile.finance.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionRequest(
    @NotBlank(message = "Title is required.") String title,
    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0.") BigDecimal amount,
    @NotBlank(message = "Type is required.") String type,
    @NotBlank(message = "Category is required.") String category,
    @NotBlank(message = "Payment method is required.") String paymentMethod,
    LocalDate transactionDate
) {}
