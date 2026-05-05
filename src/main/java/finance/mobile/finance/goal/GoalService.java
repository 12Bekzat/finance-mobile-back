package finance.mobile.finance.goal;

import finance.mobile.finance.auth.AuthService;
import finance.mobile.finance.goal.dto.AddGoalContributionRequest;
import finance.mobile.finance.goal.dto.CreateGoalRequest;
import finance.mobile.finance.goal.dto.GoalResponse;
import finance.mobile.finance.goal.dto.GoalsEnvelope;
import finance.mobile.finance.goal.dto.UpdateGoalRequest;
import finance.mobile.finance.notification.NotificationService;
import finance.mobile.finance.user.UserAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoalService {

    private final AuthService authService;
    private final GoalRepository goalRepository;
    private final NotificationService notificationService;

    public GoalService(
        AuthService authService,
        GoalRepository goalRepository,
        NotificationService notificationService
    ) {
        this.authService = authService;
        this.goalRepository = goalRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public GoalsEnvelope getGoals() {
        UserAccount user = authService.getCurrentUserEntity();
        List<GoalResponse> allGoals = goalRepository.findByUserOrderByCreatedAtDesc(user).stream().map(GoalResponse::from).toList();

        List<GoalResponse> active = allGoals.stream().filter(goal -> !goal.isCompleted()).toList();
        List<GoalResponse> completed = allGoals.stream().filter(GoalResponse::isCompleted).toList();

        return new GoalsEnvelope(active, completed);
    }

    @Transactional
    public GoalResponse createGoal(CreateGoalRequest request) {
        UserAccount user = authService.getCurrentUserEntity();
        Goal goal = new Goal();

        goal.setUser(user);
        goal.setTitle(normalizeTitle(request.title()));
        goal.setTargetAmount(normalizeMoney(request.targetAmount()));
        goal.setSavedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        goal.setTargetDate(LocalDate.now().plusDays(request.daysLeft()));
        goal.setStatus(GoalStatus.ACTIVE);

        Goal savedGoal = goalRepository.save(goal);
        notificationService.createGoalCreatedNotification(user, savedGoal);
        return GoalResponse.from(savedGoal);
    }

    @Transactional
    public GoalResponse addContribution(Long id, AddGoalContributionRequest request) {
        UserAccount user = authService.getCurrentUserEntity();
        Goal goal = goalRepository
            .findByIdAndUser(id, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found."));
        boolean wasCompleted = goal.getStatus() == GoalStatus.COMPLETED;

        BigDecimal nextSavedAmount = goal.getSavedAmount().add(normalizeMoney(request.amount())).setScale(2, RoundingMode.HALF_UP);
        goal.setSavedAmount(nextSavedAmount);

        if (nextSavedAmount.compareTo(goal.getTargetAmount()) >= 0) {
            goal.setSavedAmount(goal.getTargetAmount());
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(Instant.now());
        }

        Goal savedGoal = goalRepository.save(goal);
        if (!wasCompleted && savedGoal.getStatus() == GoalStatus.COMPLETED) {
            notificationService.createGoalCompletedNotification(user, savedGoal);
        }

        return GoalResponse.from(savedGoal);
    }

    @Transactional
    public GoalResponse updateGoal(Long id, UpdateGoalRequest request) {
        UserAccount user = authService.getCurrentUserEntity();
        Goal goal = goalRepository
            .findByIdAndUser(id, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found."));

        BigDecimal targetAmount = normalizeMoney(request.targetAmount());
        goal.setTitle(normalizeTitle(request.title()));
        goal.setTargetAmount(targetAmount);
        goal.setTargetDate(LocalDate.now().plusDays(request.daysLeft()));

        if (goal.getSavedAmount().compareTo(targetAmount) >= 0) {
            goal.setSavedAmount(targetAmount);
            goal.setStatus(GoalStatus.COMPLETED);
            if (goal.getCompletedAt() == null) {
                goal.setCompletedAt(Instant.now());
            }
        } else {
            goal.setStatus(GoalStatus.ACTIVE);
            goal.setCompletedAt(null);
        }

        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public void deleteGoal(Long id) {
        UserAccount user = authService.getCurrentUserEntity();
        Goal goal = goalRepository
            .findByIdAndUser(id, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found."));

        goalRepository.delete(goal);
    }

    private String normalizeTitle(String title) {
        String normalized = String.valueOf(title).trim();

        if (normalized.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goal title must be at least 2 characters.");
        }

        return normalized;
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0.");
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
