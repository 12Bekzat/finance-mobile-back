package finance.mobile.finance.goal;

import finance.mobile.finance.goal.dto.AddGoalContributionRequest;
import finance.mobile.finance.goal.dto.CreateGoalRequest;
import finance.mobile.finance.goal.dto.GoalResponse;
import finance.mobile.finance.goal.dto.GoalsEnvelope;
import finance.mobile.finance.goal.dto.UpdateGoalRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public GoalsEnvelope getGoals() {
        return goalService.getGoals();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse createGoal(@Valid @RequestBody CreateGoalRequest request) {
        return goalService.createGoal(request);
    }

    @PostMapping("/{id}/contributions")
    public GoalResponse addContribution(
        @PathVariable Long id,
        @Valid @RequestBody AddGoalContributionRequest request
    ) {
        return goalService.addContribution(id, request);
    }

    @PutMapping("/{id}")
    public GoalResponse updateGoal(@PathVariable Long id, @Valid @RequestBody UpdateGoalRequest request) {
        return goalService.updateGoal(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(@PathVariable Long id) {
        goalService.deleteGoal(id);
    }
}
