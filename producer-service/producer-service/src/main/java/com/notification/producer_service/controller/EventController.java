package com.notification.producer_service.controller;

import com.notification.producer_service.model.NotificationEvent;
import com.notification.producer_service.service.EventProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final Set<String> VALID_TYPES = Set.of(
            "ORDER_PLACED", "PAYMENT_SUCCESS", "PAYMENT_FAILED",
            "DELIVERY_UPDATE", "PROMO_OFFER"
    );

    private final EventProducerService producerService;

    public EventController(EventProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/fire")
    public ResponseEntity<String> fireEvent(@RequestBody NotificationEvent event) {
        if (event.getUserId() == null || event.getUserId().isBlank()) {
            return ResponseEntity.badRequest().body("userId is required");
        }
        if (event.getMessage() == null || event.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body("message is required");
        }
        if (event.getType() == null || !VALID_TYPES.contains(event.getType())) {
            return ResponseEntity.badRequest().body("Invalid event type. Valid types: " + VALID_TYPES);
        }

        event.setTimestamp(System.currentTimeMillis());
        producerService.sendEvent(event);
        return ResponseEntity.ok("Event fired successfully");
    }
}