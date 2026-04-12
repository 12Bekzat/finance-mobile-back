package finance.mobile.finance.goal.dto;

import finance.mobile.finance.goal.Goal;
import finance.mobile.finance.goal.GoalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record GoalResponse(
    Long id,
    String title,
    BigDecimal savedAmount,
    BigDecimal targetAmount,
    LocalDate targetDate,
    long daysLeft,
    String status,
    Instant completedAt,
    Instant createdAt
) {
    public static GoalResponse from(Goal goal) {
        long daysLeft = Math.max(ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate()), 0);

        return new GoalResponse(
            goal.getId(),
            goal.getTitle(),
            goal.getSavedAmount(),
            goal.getTargetAmount(),
            goal.getTargetDate(),
            daysLeft,
            goal.getStatus().name().toLowerCase(),
            goal.getCompletedAt(),
            goal.getCreatedAt()
        );
    }

    public boolean isCompleted() {
        return GoalStatus.COMPLETED.name().equalsIgnoreCase(status);
    }
}
