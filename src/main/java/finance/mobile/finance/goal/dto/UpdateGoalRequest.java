package finance.mobile.finance.goal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateGoalRequest(
    @NotBlank(message = "Goal title is required.") String title,
    @NotNull(message = "Target amount is required.")
    @DecimalMin(value = "0.01", message = "Target amount must be greater than 0.") BigDecimal targetAmount,
    @NotNull(message = "Days left is required.")
    @Min(value = 1, message = "Days left must be greater than 0.") Integer daysLeft
) {}
