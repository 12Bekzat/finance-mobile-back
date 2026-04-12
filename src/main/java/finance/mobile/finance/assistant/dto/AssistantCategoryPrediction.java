package finance.mobile.finance.assistant.dto;

import java.math.BigDecimal;

public record AssistantCategoryPrediction(
    String id,
    String name,
    String trend,
    BigDecimal amount,
    int confidence
) {}
