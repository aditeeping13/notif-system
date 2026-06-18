package com.notification.notification_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
public class SummarizationService {

    private static final Logger log = LoggerFactory.getLogger(SummarizationService.class);

    @Value("${groq.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String summarize(List<String> notificationMessages) {
        try {
            if (notificationMessages.isEmpty()) {
                return "You have no unread notifications.";
            }

            String notificationsText = String.join("\n", notificationMessages);
            String prompt = "Summarize these notifications in one short friendly sentence. " +
                    "Be concise and natural, like a personal assistant would:\n\n" +
                    notificationsText;

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "llama-3.1-8b-instant");
            requestBody.put("max_tokens", 100);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            requestBody.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String summary = root.get("choices").get(0)
                        .get("message").get("content").asText();
                log.info("Summary generated: {}", summary);
                return summary;
            } else {
                log.error("Groq API error: {}", response.body());
                return "Unable to generate summary right now.";
            }

        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            return "Unable to generate summary right now.";
        }
    }
}