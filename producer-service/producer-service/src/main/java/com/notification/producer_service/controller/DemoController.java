package com.notification.producer_service.controller;

import com.notification.producer_service.model.NotificationEvent;
import com.notification.producer_service.service.EventProducerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final EventProducerService producerService;



    public DemoController(EventProducerService producerService) {
        this.producerService = producerService;
    }

    private final Map<String, java.util.Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 3;
    private static final long WINDOW_MS = 60_000;

    private boolean isRateLimited(String userId) {
        long now = Instant.now().toEpochMilli();
        requestLog.putIfAbsent(userId, new java.util.ArrayDeque<>());
        java.util.Deque<Long> timestamps = requestLog.get(userId);

        // Remove timestamps outside the window
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS) {
            return true; // rate limited
        }

        timestamps.addLast(now);
        return false;
    }

    @PostMapping("/fire-sequence/{userId}")
    public ResponseEntity<String> fireSequence(@PathVariable String userId)
            throws InterruptedException {

        if (isRateLimited(userId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Max 3 demo sequences per minute.");
        }

        java.util.List<NotificationEvent> events = java.util.List.of(
                new NotificationEvent(userId, "ORDER_PLACED",
                        "Your order #ORD001 for Butter Chicken has been placed!", System.currentTimeMillis()),
                new NotificationEvent(userId, "PAYMENT_SUCCESS",
                        "Payment of Rs.499 received for order #ORD001", System.currentTimeMillis()),
                new NotificationEvent(userId, "DELIVERY_UPDATE",
                        "Your order #ORD001 has been picked up by the delivery partner", System.currentTimeMillis()),
                new NotificationEvent(userId, "DELIVERY_UPDATE",
                        "Your delivery partner Rohit is 2km away", System.currentTimeMillis()),
                new NotificationEvent(userId, "ORDER_PLACED",
                        "Your order #ORD002 for Margherita Pizza has been placed!", System.currentTimeMillis()),
                new NotificationEvent(userId, "PAYMENT_SUCCESS",
                        "Payment of Rs.799 received for order #ORD002", System.currentTimeMillis()),
                new NotificationEvent(userId, "PROMO_OFFER",
                        "Get 40% off on your next order! Use code SAVE40. Valid till midnight.", System.currentTimeMillis()),
                new NotificationEvent(userId, "PAYMENT_FAILED",
                        "Payment failed for order #ORD003. Please retry or use a different method.", System.currentTimeMillis()),
                new NotificationEvent(userId, "DELIVERY_UPDATE",
                        "Your order #ORD001 has been delivered. Enjoy your meal!", System.currentTimeMillis()),
                new NotificationEvent(userId, "PROMO_OFFER",
                        "You earned 50 reward points from your last order!", System.currentTimeMillis())
        );

        for (NotificationEvent event : events) {
            producerService.sendEvent(event);
            Thread.sleep(800);
        }

        return ResponseEntity.ok("Demo sequence fired — 10 events sent for " + userId);
    }
}