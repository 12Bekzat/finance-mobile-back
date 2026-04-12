package finance.mobile.finance.card;

import finance.mobile.finance.auth.AuthService;
import finance.mobile.finance.card.dto.CardResponse;
import finance.mobile.finance.card.dto.CreateCardRequest;
import finance.mobile.finance.notification.NotificationService;
import finance.mobile.finance.user.UserAccount;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CardService {

    private final AuthService authService;
    private final CardRepository cardRepository;
    private final NotificationService notificationService;

    public CardService(
        AuthService authService,
        CardRepository cardRepository,
        NotificationService notificationService
    ) {
        this.authService = authService;
        this.cardRepository = cardRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getCards() {
        UserAccount user = authService.getCurrentUserEntity();
        return cardRepository.findByUserOrderByCreatedAtDesc(user).stream().map(CardResponse::from).toList();
    }

    @Transactional
    public CardResponse createCard(CreateCardRequest request) {
        UserAccount user = authService.getCurrentUserEntity();
        String cardDigits = normalizeCardNumber(request.cardNumber());
        String holderName = normalizeHolderName(request.holderName());
        String expiry = normalizeExpiry(request.expiry());
        validateCvc(request.cvc());

        List<PaymentCard> existingCards = cardRepository.findByUserOrderByCreatedAtDesc(user);
        existingCards.forEach(card -> card.setDefault(false));

        PaymentCard card = new PaymentCard();
        card.setUser(user);
        card.setBrand(detectBrand(cardDigits));
        card.setHolderName(holderName);
        card.setLast4(cardDigits.substring(cardDigits.length() - 4));
        card.setMaskedNumber(buildMaskedNumber(cardDigits));
        card.setExpiry(expiry);
        card.setDefault(true);

        if (!existingCards.isEmpty()) {
            cardRepository.saveAll(existingCards);
        }

        PaymentCard savedCard = cardRepository.save(card);
        notificationService.createCardLinkedNotification(user, savedCard);
        return CardResponse.from(savedCard);
    }

    @Transactional
    public List<CardResponse> setDefaultCard(Long id) {
        UserAccount user = authService.getCurrentUserEntity();
        PaymentCard target = cardRepository
            .findByIdAndUser(id, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found."));

        List<PaymentCard> userCards = cardRepository.findByUserOrderByCreatedAtDesc(user);
        userCards.forEach(card -> card.setDefault(card.getId().equals(target.getId())));
        return cardRepository.saveAll(userCards).stream().map(CardResponse::from).toList();
    }

    @Transactional
    public List<CardResponse> deleteCard(Long id) {
        UserAccount user = authService.getCurrentUserEntity();
        PaymentCard target = cardRepository
            .findByIdAndUser(id, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found."));

        boolean wasDefault = target.isDefault();
        cardRepository.delete(target);

        List<PaymentCard> remaining = cardRepository.findByUserOrderByCreatedAtDesc(user);
        if (wasDefault && !remaining.isEmpty() && remaining.stream().noneMatch(PaymentCard::isDefault)) {
            remaining.get(0).setDefault(true);
            remaining = cardRepository.saveAll(remaining);
        }

        return remaining.stream().map(CardResponse::from).toList();
    }

    private String normalizeCardNumber(String cardNumber) {
        String digits = String.valueOf(cardNumber).replaceAll("\\D", "");

        if (digits.length() != 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card number must contain 16 digits.");
        }

        return digits;
    }

    private String normalizeHolderName(String holderName) {
        String normalized = String.valueOf(holderName).trim().toUpperCase();

        if (normalized.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter card holder name.");
        }

        return normalized;
    }

    private String normalizeExpiry(String expiry) {
        String normalized = String.valueOf(expiry).trim();

        if (!normalized.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must be in MM/YY format.");
        }

        return normalized;
    }

    private void validateCvc(String cvc) {
        String digits = String.valueOf(cvc).replaceAll("\\D", "");

        if (digits.length() < 3 || digits.length() > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CVC must contain 3 or 4 digits.");
        }
    }

    private String detectBrand(String rawNumber) {
        if (rawNumber.startsWith("4")) {
            return "Visa";
        }

        if (rawNumber.matches("^(5[1-5]|2[2-7]).*")) {
            return "Mastercard";
        }

        return "Card";
    }

    private String buildMaskedNumber(String rawNumber) {
        return rawNumber.substring(0, 4) + " **** **** " + rawNumber.substring(rawNumber.length() - 4);
    }
}
