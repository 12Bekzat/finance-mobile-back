package finance.mobile.finance.assistant;

import finance.mobile.finance.card.PaymentCard;
import finance.mobile.finance.goal.Goal;
import finance.mobile.finance.goal.GoalStatus;
import finance.mobile.finance.notification.UserNotification;
import finance.mobile.finance.transaction.FinanceTransaction;
import finance.mobile.finance.transaction.TransactionType;
import finance.mobile.finance.user.UserAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AssistantSnapshotFactory {

    public AssistantFinanceSnapshot build(
        UserAccount user,
        List<FinanceTransaction> transactions,
        List<Goal> goals,
        List<PaymentCard> cards,
        List<UserNotification> notifications
    ) {
        BigDecimal totalIncome = sumTransactions(transactions, transaction -> transaction.getType() == TransactionType.INCOME);
        BigDecimal totalExpenses = sumTransactions(transactions, transaction -> transaction.getType() == TransactionType.EXPENSE);
        BigDecimal currentBalance = totalIncome.subtract(totalExpenses).setScale(2, RoundingMode.HALF_UP);

        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        BigDecimal currentMonthIncome = sumTransactions(
            transactions,
            transaction ->
                transaction.getType() == TransactionType.INCOME &&
                YearMonth.from(transaction.getTransactionDate()).equals(currentMonth)
        );
        BigDecimal currentMonthExpenses = sumTransactions(
            transactions,
            transaction ->
                transaction.getType() == TransactionType.EXPENSE &&
                YearMonth.from(transaction.getTransactionDate()).equals(currentMonth)
        );
        BigDecimal previousMonthIncome = sumTransactions(
            transactions,
            transaction ->
                transaction.getType() == TransactionType.INCOME &&
                YearMonth.from(transaction.getTransactionDate()).equals(previousMonth)
        );
        BigDecimal previousMonthExpenses = sumTransactions(
            transactions,
            transaction ->
                transaction.getType() == TransactionType.EXPENSE &&
                YearMonth.from(transaction.getTransactionDate()).equals(previousMonth)
        );

        Map<YearMonth, BigDecimal> monthlyIncome = buildMonthlyTotals(transactions, TransactionType.INCOME);
        Map<YearMonth, BigDecimal> monthlyExpenses = buildMonthlyTotals(transactions, TransactionType.EXPENSE);

        BigDecimal averageMonthlyIncome = average(monthlyIncome.values().stream().toList());
        BigDecimal averageMonthlyExpenses = average(monthlyExpenses.values().stream().toList());
        BigDecimal savingsRatePercent = totalIncome.compareTo(BigDecimal.ZERO) > 0
            ? totalIncome.subtract(totalExpenses)
                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<AssistantFinanceSnapshot.SnapshotCategory> topCategories = buildTopCategories(transactions, currentMonth, previousMonth);
        List<AssistantFinanceSnapshot.SnapshotGoal> snapshotGoals = goals.stream()
            .sorted(Comparator.comparing(Goal::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(6)
            .map(goal -> new AssistantFinanceSnapshot.SnapshotGoal(
                goal.getTitle(),
                scale(goal.getTargetAmount()),
                scale(goal.getSavedAmount()),
                Math.max(ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate()), 0),
                goal.getStatus() == GoalStatus.COMPLETED
            ))
            .toList();
        List<AssistantFinanceSnapshot.SnapshotTransaction> recentTransactions = transactions.stream()
            .sorted(
                Comparator
                    .comparing(FinanceTransaction::getTransactionDate, Comparator.reverseOrder())
                    .thenComparing(FinanceTransaction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            )
            .limit(12)
            .map(transaction -> new AssistantFinanceSnapshot.SnapshotTransaction(
                transaction.getTitle(),
                transaction.getType().name().toLowerCase(),
                scale(transaction.getAmount()),
                transaction.getCategory(),
                transaction.getPaymentMethod(),
                String.valueOf(transaction.getTransactionDate())
            ))
            .toList();

        long unreadNotificationsCount = notifications.stream().filter(notification -> !notification.isRead()).count();

        return new AssistantFinanceSnapshot(
            user.getFullName(),
            user.getEmail(),
            transactions.size(),
            totalIncome,
            totalExpenses,
            currentBalance,
            currentMonthIncome,
            currentMonthExpenses,
            previousMonthIncome,
            previousMonthExpenses,
            averageMonthlyIncome,
            averageMonthlyExpenses,
            savingsRatePercent,
            (int) goals.stream().filter(goal -> goal.getStatus() != GoalStatus.COMPLETED).count(),
            (int) goals.stream().filter(goal -> goal.getStatus() == GoalStatus.COMPLETED).count(),
            cards.size(),
            unreadNotificationsCount,
            topCategories,
            snapshotGoals,
            recentTransactions,
            Instant.now()
        );
    }

    private List<AssistantFinanceSnapshot.SnapshotCategory> buildTopCategories(
        List<FinanceTransaction> transactions,
        YearMonth currentMonth,
        YearMonth previousMonth
    ) {
        Map<String, BigDecimal> totals = transactions.stream()
            .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
            .collect(
                Collectors.groupingBy(
                    FinanceTransaction::getCategory,
                    LinkedHashMap::new,
                    Collectors.reducing(
                        BigDecimal.ZERO,
                        FinanceTransaction::getAmount,
                        BigDecimal::add
                    )
                )
            );

        return totals.entrySet().stream()
            .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
            .limit(5)
            .map(entry -> new AssistantFinanceSnapshot.SnapshotCategory(
                entry.getKey(),
                scale(entry.getValue()),
                transactions.stream()
                    .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                    .filter(transaction -> entry.getKey().equalsIgnoreCase(transaction.getCategory()))
                    .count(),
                determineTrend(entry.getKey(), transactions, currentMonth, previousMonth)
            ))
            .toList();
    }

    private String determineTrend(
        String category,
        List<FinanceTransaction> transactions,
        YearMonth currentMonth,
        YearMonth previousMonth
    ) {
        BigDecimal current = transactions.stream()
            .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
            .filter(transaction -> category.equalsIgnoreCase(transaction.getCategory()))
            .filter(transaction -> YearMonth.from(transaction.getTransactionDate()).equals(currentMonth))
            .map(FinanceTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previous = transactions.stream()
            .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
            .filter(transaction -> category.equalsIgnoreCase(transaction.getCategory()))
            .filter(transaction -> YearMonth.from(transaction.getTransactionDate()).equals(previousMonth))
            .map(FinanceTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (current.compareTo(previous.multiply(BigDecimal.valueOf(1.1))) > 0) {
            return "Rising";
        }

        if (previous.compareTo(current.multiply(BigDecimal.valueOf(1.1))) > 0) {
            return "Falling";
        }

        return "Stable";
    }

    private Map<YearMonth, BigDecimal> buildMonthlyTotals(List<FinanceTransaction> transactions, TransactionType type) {
        return transactions.stream()
            .filter(transaction -> transaction.getType() == type)
            .collect(
                Collectors.groupingBy(
                    transaction -> YearMonth.from(transaction.getTransactionDate()),
                    LinkedHashMap::new,
                    Collectors.reducing(BigDecimal.ZERO, FinanceTransaction::getAmount, BigDecimal::add)
                )
            );
    }

    private BigDecimal sumTransactions(
        List<FinanceTransaction> transactions,
        java.util.function.Predicate<FinanceTransaction> predicate
    ) {
        return scale(
            transactions.stream()
                .filter(predicate)
                .map(FinanceTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
