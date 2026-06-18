package com.notification.notification_service.service;

import com.notification.notification_service.model.Notification;
import com.notification.notification_service.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final double SIMILARITY_THRESHOLD = 0.2;

    private final NotificationRepository repository;
    private final EmbeddingService embeddingService;

    public SearchService(NotificationRepository repository,
                         EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    public List<Notification> search(String userId, String query, int topK) {
        log.info("Semantic search for user: {} query: {}", userId, query);

        List<Double> queryEmbedding = embeddingService.generateQueryEmbedding(query);
        if (queryEmbedding == null) {
            log.error("Failed to generate query embedding");
            return List.of();
        }

        List<Notification> notifications = repository
                .findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .filter(n -> n.getEmbedding() != null)
                .collect(Collectors.toList());

        if (notifications.isEmpty()) {
            log.warn("No notifications with embeddings found for user: {}", userId);
            return List.of();
        }

        List<ScoredNotification> scored = notifications.stream()
                .map(n -> new ScoredNotification(n,
                        embeddingService.cosineSimilarity(queryEmbedding, n.getEmbedding())))
                .sorted(Comparator.comparingDouble(ScoredNotification::score).reversed())
                .collect(Collectors.toList());

        // Log top scores to help tune the threshold
        scored.stream().limit(3).forEach(sn ->
                log.info("  score={} type={} msg={}",
                        String.format("%.4f", sn.score()),
                        sn.notification().getType(),
                        sn.notification().getMessage().substring(0, Math.min(40, sn.notification().getMessage().length())))
        );

        List<Notification> results = scored.stream()
                .filter(sn -> sn.score() >= SIMILARITY_THRESHOLD)
                .limit(topK)
                .map(ScoredNotification::notification)
                .collect(Collectors.toList());

        log.info("Search returned {} results (threshold={})", results.size(), SIMILARITY_THRESHOLD);
        return results;
    }

    private record ScoredNotification(Notification notification, double score) {}
}