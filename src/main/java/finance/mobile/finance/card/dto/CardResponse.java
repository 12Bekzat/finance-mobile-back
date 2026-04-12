package finance.mobile.finance.card.dto;

import finance.mobile.finance.card.PaymentCard;
import java.time.Instant;

public record CardResponse(
    Long id,
    String brand,
    String holderName,
    String maskedNumber,
    String last4,
    String expiry,
    boolean isDefault,
    Instant createdAt
) {
    public static CardResponse from(PaymentCard card) {
        return new CardResponse(
            card.getId(),
            card.getBrand(),
            card.getHolderName(),
            card.getMaskedNumber(),
            card.getLast4(),
            card.getExpiry(),
            card.isDefault(),
            card.getCreatedAt()
        );
    }
}
