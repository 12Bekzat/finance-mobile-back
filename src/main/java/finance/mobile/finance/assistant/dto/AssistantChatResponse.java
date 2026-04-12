package finance.mobile.finance.assistant.dto;

import java.time.Instant;
import java.util.List;

public record AssistantChatResponse(
    String answer,
    List<String> followUpSuggestions,
    Instant generatedAt,
    String model
) {}
