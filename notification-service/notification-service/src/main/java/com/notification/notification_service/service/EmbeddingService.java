package com.notification.notification_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${cohere.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Double> generateEmbedding(String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    new CohereEmbedRequest(
                            List.of(text),
                            "embed-english-light-v3.0",
                            "search_document"
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.com/v1/embed"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode embeddingNode = root.get("embeddings").get(0);
                List<Double> embedding = new ArrayList<>();
                embeddingNode.forEach(v -> embedding.add(v.asDouble()));
                log.info("Embedding generated, size: {}", embedding.size());
                return embedding;
            } else {
                log.error("Cohere API error: {}", response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            return null;
        }
    }

    public double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        if (vecA == null || vecB == null || vecA.size() != vecB.size()) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.size(); i++) {
            dotProduct += vecA.get(i) * vecB.get(i);
            normA += Math.pow(vecA.get(i), 2);
            normB += Math.pow(vecB.get(i), 2);
        }

        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public List<Double> generateQueryEmbedding(String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    new CohereEmbedRequest(
                            List.of(text),
                            "embed-english-light-v3.0",
                            "search_query"
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.com/v1/embed"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode embeddingNode = root.get("embeddings").get(0);
                List<Double> embedding = new ArrayList<>();
                embeddingNode.forEach(v -> embedding.add(v.asDouble()));
                return embedding;
            } else {
                log.error("Cohere API error: {}", response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to generate query embedding", e);
            return null;
        }
    }

    record CohereEmbedRequest(List<String> texts, String model, String input_type) {}
}