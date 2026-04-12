package finance.mobile.finance.card;

import finance.mobile.finance.card.dto.CardResponse;
import finance.mobile.finance.card.dto.CreateCardRequest;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public List<CardResponse> getCards() {
        return cardService.getCards();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse createCard(@Valid @RequestBody CreateCardRequest request) {
        return cardService.createCard(request);
    }

    @PutMapping("/{id}/default")
    public List<CardResponse> setDefaultCard(@PathVariable Long id) {
        return cardService.setDefaultCard(id);
    }

    @DeleteMapping("/{id}")
    public List<CardResponse> deleteCard(@PathVariable Long id) {
        return cardService.deleteCard(id);
    }
}
