package finance.mobile.finance.assistant;

import finance.mobile.finance.assistant.dto.AssistantAnalysisResponse;
import finance.mobile.finance.assistant.dto.AssistantCategoryPrediction;
import finance.mobile.finance.assistant.dto.AssistantChatResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OpenAiAssistantClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-5.2}")
    private String model;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    public OpenAiAssistantClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AssistantAnalysisResponse generateAnalysis(AssistantFinanceSnapshot snapshot) {
        JsonNode json = createJsonResponse(buildAnalysisInstructions(), buildAnalysisInput(snapshot), 1400);
        return normalizeAnalysis(json, snapshot);
    }

    public AssistantChatResponse generateChat(AssistantFinanceSnapshot snapshot, String message) {
        JsonNode json = createJsonResponse(buildChatInstructions(), buildChatInput(snapshot, message), 900);

        String answer = readText(json, "answer");
        if (answer.isBlank()) {
            answer = "I couldn't generate a useful answer from your current finance data. Try a more specific question.";
        }

        List<String> followUpSuggestions = readStringList(json, "followUpSuggestions", defaultSuggestedQuestions(snapshot));

        return new AssistantChatResponse(answer, followUpSuggestions, Instant.now(), model);
    }

    private JsonNode createJsonResponse(String instructions, String input, int maxOutputTokens) {
        ensureConfigured();

        try {
            if (shouldPreferChatCompletions()) {
                return createJsonResponseViaChatCompletions(instructions, input, maxOutputTokens, true);
            }

            return createJsonResponseViaResponses(instructions, input, maxOutputTokens, true);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to read AI assistant response.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI assistant request was interrupted.");
        }
    }

    private JsonNode createJsonResponseViaResponses(
        String instructions,
        String input,
        int maxOutputTokens,
        boolean allowChatFallback
    ) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
            buildResponsesRequest(instructions, input, maxOutputTokens),
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 404 && allowChatFallback) {
            return createJsonResponseViaChatCompletions(instructions, input, maxOutputTokens, false);
        }

        assertSuccessfulResponse(response);
        JsonNode root = objectMapper.readTree(response.body());
        String outputText = extractOutputText(root);

        if (outputText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI assistant returned an empty response.");
        }

        return objectMapper.readTree(outputText);
    }

    private JsonNode createJsonResponseViaChatCompletions(
        String instructions,
        String input,
        int maxOutputTokens,
        boolean allowResponsesFallback
    )
        throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
            buildChatCompletionsRequest(instructions, input, maxOutputTokens),
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 404 && allowResponsesFallback) {
            return createJsonResponseViaResponses(instructions, input, maxOutputTokens, false);
        }

        assertSuccessfulResponse(response);

        JsonNode root = objectMapper.readTree(response.body());
        String content = root
            .path("choices")
            .path(0)
            .path("message")
            .path("content")
            .asText("")
            .trim();

        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI assistant returned an empty response.");
        }

        try {
            return objectMapper.readTree(content);
        } catch (RuntimeException exception) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("answer", content);
            ArrayNode suggestions = objectMapper.createArrayNode();
            defaultSuggestedQuestions(null).forEach(suggestions::add);
            fallback.set("followUpSuggestions", suggestions);
            return fallback;
        }
    }

    private HttpRequest buildResponsesRequest(String instructions, String input, int maxOutputTokens) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", instructions);
        body.put("input", input);
        body.put("max_output_tokens", maxOutputTokens);

        ObjectNode textNode = body.putObject("text");
        ObjectNode formatNode = textNode.putObject("format");
        formatNode.put("type", "json_object");

        return authorizedPost("/responses", objectMapper.writeValueAsString(body));
    }

    private HttpRequest buildChatCompletionsRequest(String instructions, String input, int maxOutputTokens) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxOutputTokens);

        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");

        ArrayNode messages = objectMapper.createArrayNode();
        messages.addObject()
            .put("role", "system")
            .put("content", instructions);
        messages.addObject()
            .put("role", "user")
            .put("content", input);

        body.set("messages", messages);

        return authorizedPost("/chat/completions", objectMapper.writeValueAsString(body));
    }

    private HttpRequest authorizedPost(String path, String payload) {
        return HttpRequest.newBuilder()
            .uri(URI.create(trimTrailingSlash(baseUrl) + path))
            .header("Authorization", "Bearer " + apiKey.trim())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
    }

    private void assertSuccessfulResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                extractUpstreamError(response.body(), response.statusCode())
            );
        }
    }

    private AssistantAnalysisResponse normalizeAnalysis(JsonNode json, AssistantFinanceSnapshot snapshot) {
        BigDecimal predictedIncome = readMoney(
            json,
            "predictedIncome",
            choosePositiveFallback(snapshot.currentMonthIncome(), snapshot.averageMonthlyIncome())
        );
        BigDecimal predictedExpenses = readMoney(
            json,
            "predictedExpenses",
            choosePositiveFallback(snapshot.currentMonthExpenses(), snapshot.averageMonthlyExpenses())
        );
        BigDecimal savingsPotential = readMoney(
            json,
            "savingsPotential",
            predictedIncome.subtract(predictedExpenses).max(BigDecimal.ZERO)
        );

        String riskLevel = normalizeRiskLevel(readText(json, "riskLevel"), predictedIncome, predictedExpenses);
        String riskMessage = readText(json, "riskMessage");
        if (riskMessage.isBlank()) {
            riskMessage = defaultRiskMessage(riskLevel);
        }

        List<AssistantCategoryPrediction> categories = readCategories(json.path("categories"), snapshot);
        List<String> recommendations = readStringList(json, "recommendations", defaultRecommendations(snapshot));
        List<String> suggestedQuestions = readStringList(json, "suggestedQuestions", defaultSuggestedQuestions(snapshot));

        return new AssistantAnalysisResponse(
            predictedIncome,
            predictedExpenses,
            savingsPotential,
            riskLevel,
            riskMessage,
            categories,
            recommendations,
            suggestedQuestions,
            Instant.now(),
            model
        );
    }

    private List<AssistantCategoryPrediction> readCategories(JsonNode node, AssistantFinanceSnapshot snapshot) {
        List<AssistantCategoryPrediction> categories = new ArrayList<>();

        if (node.isArray()) {
            for (JsonNode item : node) {
                String name = readText(item, "name");
                if (name.isBlank()) {
                    continue;
                }

                categories.add(new AssistantCategoryPrediction(
                    UUID.randomUUID().toString(),
                    name,
                    normalizeTrend(readText(item, "trend")),
                    readMoney(item, "amount", BigDecimal.ZERO),
                    clampConfidence(item.path("confidence").asInt(55))
                ));

                if (categories.size() == 5) {
                    break;
                }
            }
        }

        if (!categories.isEmpty()) {
            return categories;
        }

        return snapshot.topCategories().stream()
            .map(category -> new AssistantCategoryPrediction(
                UUID.randomUUID().toString(),
                category.name(),
                normalizeTrend(category.trend()),
                category.amount(),
                55
            ))
            .toList();
    }

    private List<String> readStringList(JsonNode json, String field, List<String> fallback) {
        List<String> values = new ArrayList<>();
        JsonNode node = json.path(field);

        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }

        return values.isEmpty() ? fallback : values.stream().limit(4).toList();
    }

    private BigDecimal readMoney(JsonNode json, String field, BigDecimal fallback) {
        JsonNode node = json.path(field);

        if (node.isNumber()) {
            return node.decimalValue().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().replaceAll("[^0-9.-]", ""))
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                return fallback.setScale(2, RoundingMode.HALF_UP);
            }
        }

        return fallback.setScale(2, RoundingMode.HALF_UP);
    }

    private String readText(JsonNode json, String field) {
        return json.path(field).asText("").trim();
    }

    private BigDecimal choosePositiveFallback(BigDecimal primary, BigDecimal secondary) {
        if (primary != null && primary.compareTo(BigDecimal.ZERO) > 0) {
            return primary.setScale(2, RoundingMode.HALF_UP);
        }

        if (secondary != null && secondary.compareTo(BigDecimal.ZERO) > 0) {
            return secondary.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeRiskLevel(String rawRiskLevel, BigDecimal predictedIncome, BigDecimal predictedExpenses) {
        String normalized = rawRiskLevel.trim();
        if (normalized.equalsIgnoreCase("low")) {
            return "Low";
        }
        if (normalized.equalsIgnoreCase("medium")) {
            return "Medium";
        }
        if (normalized.equalsIgnoreCase("high")) {
            return "High";
        }

        if (predictedIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return "Medium";
        }

        BigDecimal ratio = predictedExpenses.divide(predictedIncome, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.valueOf(0.6)) < 0) {
            return "Low";
        }
        if (ratio.compareTo(BigDecimal.valueOf(0.85)) < 0) {
            return "Medium";
        }
        return "High";
    }

    private String normalizeTrend(String rawTrend) {
        String normalized = rawTrend.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "rising", "up", "increase", "increasing" -> "Rising";
            case "falling", "down", "decrease", "decreasing" -> "Falling";
            default -> "Stable";
        };
    }

    private int clampConfidence(int confidence) {
        return Math.max(25, Math.min(confidence, 95));
    }

    private String extractOutputText(JsonNode root) {
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return "";
        }

        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }

            for (JsonNode part : content) {
                String type = part.path("type").asText("");
                if ("output_text".equals(type) || "text".equals(type)) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }

        return "";
    }

    private String extractUpstreamError(String body, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            String message = error.path("message").asText("");
            if (!message.isBlank()) {
                return "AI assistant request failed: " + message;
            }
        } catch (RuntimeException ignored) {
            String trimmed = String.valueOf(body).trim();
            if (!trimmed.isBlank()) {
                return "AI assistant request failed: " + trimmed;
            }
        }

        return "AI assistant request failed with upstream status " + statusCode + ".";
    }

    private String buildAnalysisInstructions() {
        return """
            You are a cautious finance assistant for a personal budgeting app.
            You are given real user finance data from the application database.
            Return only a valid json object with this shape:
            {
              "predictedIncome": number,
              "predictedExpenses": number,
              "savingsPotential": number,
              "riskLevel": "Low" | "Medium" | "High",
              "riskMessage": string,
              "categories": [
                { "name": string, "trend": "Rising" | "Stable" | "Falling", "amount": number, "confidence": number }
              ],
              "recommendations": [string],
              "suggestedQuestions": [string]
            }
            Rules:
            - Use concise, realistic numbers in USD.
            - Base predictions only on provided data.
            - If the data is sparse, say so in recommendations and keep confidence lower.
            - Recommendations must be actionable and specific to the data.
            - Keep riskMessage under 180 characters.
            - Return no markdown and no prose outside json.
            """;
    }

    private String buildChatInstructions() {
        return """
            You are a pragmatic personal finance assistant inside a mobile finance app.
            You receive real account data and a user question.
            Return only a valid json object with this shape:
            {
              "answer": string,
              "followUpSuggestions": [string]
            }
            Rules:
            - Answer from the provided data only.
            - Be concise and practical.
            - If data is missing, say exactly what is missing.
            - Do not claim certainty about future outcomes.
            - Return no markdown code fences and no prose outside json.
            """;
    }

    private String buildAnalysisInput(AssistantFinanceSnapshot snapshot) {
        return "json response required.\nUser finance snapshot:\n" + objectMapper.writeValueAsString(snapshot.toPromptPayload());
    }

    private String buildChatInput(AssistantFinanceSnapshot snapshot, String message) {
        return """
            json response required.
            User finance snapshot:
            %s

            User question:
            %s
            """.formatted(objectMapper.writeValueAsString(snapshot.toPromptPayload()), message.trim());
    }

    private List<String> defaultRecommendations(AssistantFinanceSnapshot snapshot) {
        List<String> recommendations = new ArrayList<>();

        if (snapshot.transactionCount() == 0) {
            recommendations.add("Start adding income and expenses regularly so the assistant can build a reliable forecast.");
        } else {
            if (snapshot.currentMonthExpenses().compareTo(snapshot.currentMonthIncome()) > 0 &&
                snapshot.currentMonthIncome().compareTo(BigDecimal.ZERO) > 0) {
                recommendations.add("This month expenses are ahead of income. Review discretionary categories before the month closes.");
            }

            if (!snapshot.topCategories().isEmpty()) {
                recommendations.add("Review " + snapshot.topCategories().get(0).name() + " first. It is your largest expense category right now.");
            }

            if (snapshot.activeGoalsCount() > 0 && snapshot.currentBalance().compareTo(BigDecimal.ZERO) > 0) {
                recommendations.add("Channel part of your positive balance into active goals to improve goal completion speed.");
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Keep tracking expenses consistently so the assistant can detect stronger patterns.");
        }

        return recommendations.stream().limit(4).toList();
    }

    private List<String> defaultSuggestedQuestions(AssistantFinanceSnapshot snapshot) {
        List<String> questions = new ArrayList<>();
        questions.add("Where can I cut spending this month?");
        questions.add("What category looks the riskiest right now?");
        questions.add("How much can I put toward my goals safely?");

        if (snapshot != null && snapshot.currentMonthExpenses().compareTo(snapshot.currentMonthIncome()) > 0) {
            questions.add("Why are my expenses above income this month?");
        }

        return questions.stream().distinct().limit(4).toList();
    }

    private String defaultRiskMessage(String riskLevel) {
        return switch (riskLevel) {
            case "Low" -> "Your tracked spending is well below income. Keep that gap stable and protect it.";
            case "High" -> "Your tracked expenses are close to or above income. Tighten variable categories this month.";
            default -> "Your spending is manageable, but a few category spikes could reduce savings if they continue.";
        };
    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI assistant is not configured on the server. Add OPENAI_API_KEY to finance-server/.env."
            );
        }
    }

    private boolean shouldPreferChatCompletions() {
        try {
            URI uri = URI.create(trimTrailingSlash(baseUrl));
            String host = String.valueOf(uri.getHost()).toLowerCase(Locale.ROOT);
            return !"api.openai.com".equals(host);
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private String trimTrailingSlash(String value) {
        return String.valueOf(value).replaceAll("/+$", "");
    }
}
