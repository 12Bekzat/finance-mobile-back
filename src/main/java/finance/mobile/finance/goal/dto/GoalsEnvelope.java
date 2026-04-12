package finance.mobile.finance.goal.dto;

import java.util.List;

public record GoalsEnvelope(List<GoalResponse> active, List<GoalResponse> completed) {}
