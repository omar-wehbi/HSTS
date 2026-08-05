package server.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adapter for an existing external bot API. */
public class ExternalStudyBotApiAdapter implements StudyBotApi {
    private static final int MAX_CONTEXT_CHARS = 15_000;

    private final String endpoint, apiKey, model;
    private final HttpClient client;
    private final ObjectMapper json = new ObjectMapper();

    public ExternalStudyBotApiAdapter() {
        this(System.getenv("HSTS_BOT_API_URL"), System.getenv("HSTS_BOT_API_KEY"), System.getenv("HSTS_BOT_MODEL"));
    }

    public ExternalStudyBotApiAdapter(String endpoint, String apiKey) {
        this(endpoint, apiKey, null);
    }

    public ExternalStudyBotApiAdapter(String endpoint, String apiKey, String model) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "mistralai/mistral-medium-3.5-128b" : model;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override public String ask(String question, List<String> sources) throws Exception {
        if (endpoint == null || endpoint.isBlank())
            throw new IllegalStateException("Study bot API is not configured.");

        StringBuilder context = new StringBuilder();
        for (String source : sources) {
            if (source == null || source.isBlank()) continue;
            int remaining = MAX_CONTEXT_CHARS - context.length();
            if (remaining <= 0) break;
            if (context.length() > 0) context.append("\n\n");
            context.append(source, 0, Math.min(source.length(), remaining));
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (context.length() > 0) {
            messages.add(Map.of("role", "system",
                    "content", "Use the following course material to answer the student's question:\n\n" + context));
        }
        messages.add(Map.of("role", "user", "content", question));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", false);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload), StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException(
                    "External bot service failed with HTTP " + response.statusCode()
            );

        JsonNode root = json.readTree(response.body());
        String answer = text(root, "answer");
        if (answer == null) answer = text(root, "response");
        if (answer == null && root.has("choices") && root.path("choices").isArray() && root.path("choices").size() > 0)
            answer = root.path("choices").get(0).path("message").path("content").asText(null);
        if (answer == null || answer.isBlank())
            throw new IllegalStateException("No suitable answer was returned.");
        return answer.trim();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }
}