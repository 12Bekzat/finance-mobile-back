package finance.mobile.finance.assistant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record AssistantFinanceSnapshot(
    String fullName,
    String email,
    int transactionCount,
    BigDecimal totalIncome,
    BigDecimal totalExpenses,
    BigDecimal currentBalance,
    BigDecimal currentMonthIncome,
    BigDecimal currentMonthExpenses,
    BigDecimal previousMonthIncome,
    BigDecimal previousMonthExpenses,
    BigDecimal averageMonthlyIncome,
    BigDecimal averageMonthlyExpenses,
    BigDecimal savingsRatePercent,
    int activeGoalsCount,
    int completedGoalsCount,
    int linkedCardsCount,
    long unreadNotificationsCount,
    List<SnapshotCategory> topCategories,
    List<SnapshotGoal> goals,
    List<SnapshotTransaction> recentTransactions,
    Instant generatedAt
) {

    Map<String, Object> toPromptPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("transactionCount", transactionCount);
        summary.put("totalIncome", totalIncome);
        summary.put("totalExpenses", totalExpenses);
        summary.put("currentBalance", currentBalance);
        summary.put("currentMonthIncome", currentMonthIncome);
        summary.put("currentMonthExpenses", currentMonthExpenses);
        summary.put("previousMonthIncome", previousMonthIncome);
        summary.put("previousMonthExpenses", previousMonthExpenses);
        summary.put("averageMonthlyIncome", averageMonthlyIncome);
        summary.put("averageMonthlyExpenses", averageMonthlyExpenses);
        summary.put("savingsRatePercent", savingsRatePercent);

        payload.put("profile", Map.of(
            "fullName", fullName,
            "email", email
        ));
        payload.put("summary", summary);
        payload.put("goals", Map.of(
            "activeCount", activeGoalsCount,
            "completedCount", completedGoalsCount,
            "items", goals
        ));
        payload.put("wallet", Map.of(
            "linkedCardsCount", linkedCardsCount,
            "unreadNotificationsCount", unreadNotificationsCount
        ));
        payload.put("topExpenseCategories", topCategories);
        payload.put("recentTransactions", recentTransactions);
        payload.put("generatedAt", generatedAt);
        return payload;
    }

    record SnapshotCategory(
        String name,
        BigDecimal amount,
        long transactions,
        String trend
    ) {}

    record SnapshotGoal(
        String title,
        BigDecimal targetAmount,
        BigDecimal savedAmount,
        long daysLeft,
        boolean completed
    ) {}

    record SnapshotTransaction(
        String title,
        String type,
        BigDecimal amount,
        String category,
        String paymentMethod,
        String transactionDate
    ) {}
}
