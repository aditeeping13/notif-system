package com.notification.notification_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.notification_service.model.Notification;
import com.notification.notification_service.model.User;
import com.notification.notification_service.repository.NotificationRepository;
import com.notification.notification_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationConsumerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumerService.class);
    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmbeddingService embeddingService;
    private final DlqProducerService dlqProducerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationConsumerService(NotificationRepository repository,
                                       UserRepository userRepository,
                                       SimpMessagingTemplate messagingTemplate,
                                       EmbeddingService embeddingService,
                                       DlqProducerService dlqProducerService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.embeddingService = embeddingService;
        this.dlqProducerService = dlqProducerService;
    }

    @KafkaListener(topics = "notifications", groupId = "notification-group")
    public void consume(String message) {
        try {
            log.info("Received from Kafka: {}", message);
            Notification notification = objectMapper.readValue(message, Notification.class);

            // Check mute preferences
            Optional<User> user = userRepository.findByUsername(notification.getUserId());
            if (user.isPresent() && user.get().getMutedTypes().contains(notification.getType())) {
                log.info("Notification muted for user: {} type: {}", notification.getUserId(), notification.getType());
                return;
            }

            notification.setRead(false);
            notification.setStatus("DELIVERED");
            repository.save(notification);
            log.info("Notification saved to MongoDB for user: {}", notification.getUserId());

            // Generate embedding — send to DLQ if fails
            try {
                String textToEmbed = notification.getType() + " " + notification.getMessage();
                List<Double> embedding = embeddingService.generateEmbedding(textToEmbed);
                if (embedding != null) {
                    notification.setEmbedding(embedding);
                    repository.save(notification);
                    log.info("Embedding stored for notification: {}", notification.getId());
                } else {
                    log.warn("Embedding returned null for notification: {}", notification.getId());
                    dlqProducerService.sendToDlq(message, "EMBEDDING_FAILED");
                }
            } catch (Exception e) {
                log.error("Embedding generation failed, sending to DLQ: {}", e.getMessage());
                dlqProducerService.sendToDlq(message, "EMBEDDING_ERROR: " + e.getMessage());
            }

            // Push to WebSocket
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + notification.getUserId(),
                    notification
            );
            log.info("Pushed to WebSocket for user: {}", notification.getUserId());

        } catch (Exception e) {
            log.error("Critical failure processing notification, sending to DLQ: {}", e.getMessage());
            dlqProducerService.sendToDlq(message, "PROCESSING_ERROR: " + e.getMessage());
        }
    }

    // DLQ consumer — logs failed messages for inspection
    @KafkaListener(topics = "notifications-dlq", groupId = "dlq-group")
    public void consumeDlq(String message) {
        log.warn("DLQ message received: {}", message);
        // In production: store in DB, alert admin, retry logic
    }
}