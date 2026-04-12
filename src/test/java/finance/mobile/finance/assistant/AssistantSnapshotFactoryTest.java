package finance.mobile.finance.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import finance.mobile.finance.card.PaymentCard;
import finance.mobile.finance.goal.Goal;
import finance.mobile.finance.goal.GoalStatus;
import finance.mobile.finance.notification.NotificationType;
import finance.mobile.finance.notification.UserNotification;
import finance.mobile.finance.transaction.FinanceTransaction;
import finance.mobile.finance.transaction.TransactionType;
import finance.mobile.finance.user.UserAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantSnapshotFactoryTest {

    private final AssistantSnapshotFactory assistantSnapshotFactory = new AssistantSnapshotFactory();

    @Test
    void buildsSnapshotFromRealFinanceData() {
        UserAccount user = new UserAccount();
        user.setFullName("Test User");
        user.setEmail("test@example.com");

        FinanceTransaction salary = transaction(user, "Salary", TransactionType.INCOME, "Salary", "Bank Transfer", 1200, LocalDate.now().minusDays(2));
        FinanceTransaction groceries = transaction(user, "Groceries", TransactionType.EXPENSE, "Food", "Card", 180, LocalDate.now().minusDays(1));
        FinanceTransaction fuel = transaction(user, "Fuel", TransactionType.EXPENSE, "Transport", "Card", 90, LocalDate.now().minusDays(4));

        Goal goal = new Goal();
        goal.setUser(user);
        goal.setTitle("Emergency Fund");
        goal.setTargetAmount(BigDecimal.valueOf(500));
        goal.setSavedAmount(BigDecimal.valueOf(200));
        goal.setTargetDate(LocalDate.now().plusDays(30));
        goal.setStatus(GoalStatus.ACTIVE);

        PaymentCard card = new PaymentCard();
        card.setUser(user);
        card.setBrand("Visa");
        card.setHolderName("Test User");
        card.setMaskedNumber("**** **** **** 4242");
        card.setLast4("4242");
        card.setExpiry("12/28");
        card.setDefault(true);

        UserNotification notification = new UserNotification();
        notification.setUser(user);
        notification.setType(NotificationType.GOAL_CREATED);
        notification.setTitle("Goal created");
        notification.setMessage("Emergency Fund created.");
        notification.setRead(false);

        AssistantFinanceSnapshot snapshot = assistantSnapshotFactory.build(
            user,
            List.of(salary, groceries, fuel),
            List.of(goal),
            List.of(card),
            List.of(notification)
        );

        assertEquals(new BigDecimal("1200.00"), snapshot.totalIncome());
        assertEquals(new BigDecimal("270.00"), snapshot.totalExpenses());
        assertEquals(new BigDecimal("930.00"), snapshot.currentBalance());
        assertEquals(1, snapshot.activeGoalsCount());
        assertEquals(1, snapshot.linkedCardsCount());
        assertEquals(1L, snapshot.unreadNotificationsCount());
        assertFalse(snapshot.topCategories().isEmpty());
        assertEquals("Food", snapshot.topCategories().get(0).name());
    }

    private FinanceTransaction transaction(
        UserAccount user,
        String title,
        TransactionType type,
        String category,
        String paymentMethod,
        int amount,
        LocalDate date
    ) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUser(user);
        transaction.setTitle(title);
        transaction.setType(type);
        transaction.setCategory(category);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setAmount(BigDecimal.valueOf(amount).setScale(2));
        transaction.setTransactionDate(date);
        return transaction;
    }
}
