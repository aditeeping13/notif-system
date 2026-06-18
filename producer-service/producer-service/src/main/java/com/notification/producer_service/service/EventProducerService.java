package com.notification.producer_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.producer_service.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventProducerService {

    private static final Logger log = LoggerFactory.getLogger(EventProducerService.class);
    private static final String TOPIC = "notifications";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(NotificationEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getUserId(), eventJson);
            log.info("Event sent to Kafka: {}", eventJson);
        } catch (Exception e) {
            log.error("Failed to send event to Kafka", e);
        }
    }
}