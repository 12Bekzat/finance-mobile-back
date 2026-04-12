package finance.mobile.finance.assistant;

import finance.mobile.finance.assistant.dto.AssistantAnalysisResponse;
import finance.mobile.finance.assistant.dto.AssistantChatRequest;
import finance.mobile.finance.assistant.dto.AssistantChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/analysis")
    public AssistantAnalysisResponse getAnalysis() {
        return assistantService.getAnalysis();
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        return assistantService.chat(request);
    }
}
