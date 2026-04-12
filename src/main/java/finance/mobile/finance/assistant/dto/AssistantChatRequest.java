package finance.mobile.finance.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
    @NotBlank(message = "Message is required.")
    @Size(max = 1200, message = "Message must be at most 1200 characters.")
    String message
) {}
