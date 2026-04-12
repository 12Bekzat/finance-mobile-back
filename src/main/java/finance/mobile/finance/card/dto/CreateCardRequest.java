package finance.mobile.finance.card.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCardRequest(
    @NotBlank(message = "Card holder name is required.") String holderName,
    @NotBlank(message = "Card number is required.") String cardNumber,
    @NotBlank(message = "Expiry is required.") String expiry,
    @NotBlank(message = "CVC is required.") String cvc
) {}
