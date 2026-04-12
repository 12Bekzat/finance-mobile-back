package finance.mobile.finance.assistant;

import finance.mobile.finance.assistant.dto.AssistantAnalysisResponse;
import finance.mobile.finance.assistant.dto.AssistantChatRequest;
import finance.mobile.finance.assistant.dto.AssistantChatResponse;
import finance.mobile.finance.auth.AuthService;
import finance.mobile.finance.card.CardRepository;
import finance.mobile.finance.goal.GoalRepository;
import finance.mobile.finance.notification.NotificationRepository;
import finance.mobile.finance.transaction.TransactionRepository;
import finance.mobile.finance.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantService {

    private final AuthService authService;
    private final TransactionRepository transactionRepository;
    private final GoalRepository goalRepository;
    private final CardRepository cardRepository;
    private final NotificationRepository notificationRepository;
    private final AssistantSnapshotFactory assistantSnapshotFactory;
    private final OpenAiAssistantClient openAiAssistantClient;

    public AssistantService(
        AuthService authService,
        TransactionRepository transactionRepository,
        GoalRepository goalRepository,
        CardRepository cardRepository,
        NotificationRepository notificationRepository,
        AssistantSnapshotFactory assistantSnapshotFactory,
        OpenAiAssistantClient openAiAssistantClient
    ) {
        this.authService = authService;
        this.transactionRepository = transactionRepository;
        this.goalRepository = goalRepository;
        this.cardRepository = cardRepository;
        this.notificationRepository = notificationRepository;
        this.assistantSnapshotFactory = assistantSnapshotFactory;
        this.openAiAssistantClient = openAiAssistantClient;
    }

    @Transactional(readOnly = true)
    public AssistantAnalysisResponse getAnalysis() {
        return openAiAssistantClient.generateAnalysis(buildSnapshot());
    }

    @Transactional(readOnly = true)
    public AssistantChatResponse chat(AssistantChatRequest request) {
        return openAiAssistantClient.generateChat(buildSnapshot(), request.message());
    }

    private AssistantFinanceSnapshot buildSnapshot() {
        UserAccount user = authService.getCurrentUserEntity();

        return assistantSnapshotFactory.build(
            user,
            transactionRepository.findByUserOrderByTransactionDateDescCreatedAtDesc(user),
            goalRepository.findByUserOrderByCreatedAtDesc(user),
            cardRepository.findByUserOrderByCreatedAtDesc(user),
            notificationRepository.findByUserOrderByCreatedAtDesc(user)
        );
    }
}
