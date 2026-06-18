package com.notification.notification_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DlqProducerService {

    private static final Logger log = LoggerFactory.getLogger(DlqProducerService.class);
    private static final String DLQ_TOPIC = "notifications-dlq";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DlqProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendToDlq(String message, String reason) {
        try {
            String dlqMessage = "{\"reason\":\"" + reason + "\",\"originalMessage\":" + message + "}";
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Message sent to DLQ. Reason: {}", reason);
        } catch (Exception e) {
            log.error("Failed to send message to DLQ", e);
        }
    }
}