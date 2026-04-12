package finance.mobile.finance.assistant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AssistantAnalysisResponse(
    BigDecimal predictedIncome,
    BigDecimal predictedExpenses,
    BigDecimal savingsPotential,
    String riskLevel,
    String riskMessage,
    List<AssistantCategoryPrediction> categories,
    List<String> recommendations,
    List<String> suggestedQuestions,
    Instant generatedAt,
    String model
) {}
