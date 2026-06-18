package com.notification.notification_service.controller;

import com.notification.notification_service.model.Notification;
import com.notification.notification_service.repository.NotificationRepository;
import com.notification.notification_service.repository.UserRepository;
import com.notification.notification_service.service.SearchService;
import com.notification.notification_service.service.SummarizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")

public class NotificationController {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final SearchService searchService;
    private final SummarizationService summarizationService;
    private final Map<String, java.util.Deque<Long>> searchLog = new ConcurrentHashMap<>();
    private final Map<String, java.util.Deque<Long>> summaryLog = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000;

    private boolean isRateLimited(Map<String, java.util.Deque<Long>> log, String userId, int maxRequests) {
        long now = Instant.now().toEpochMilli();
        log.putIfAbsent(userId, new java.util.ArrayDeque<>());
        java.util.Deque<Long> timestamps = log.get(userId);

        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return true;
        }

        timestamps.addLast(now);
        return false;
    }

    public NotificationController(NotificationRepository repository,
                                  UserRepository userRepository,
                                  SearchService searchService,
                                  SummarizationService summarizationService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.searchService = searchService;
        this.summarizationService = summarizationService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<Notification>> getNotifications(@PathVariable String userId) {
        return ResponseEntity.ok(repository.findByUserIdOrderByTimestampDesc(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<String> markAsRead(@PathVariable String id) {
        repository.findById(id).ifPresent(n -> {
            n.setRead(true);
            repository.save(n);
        });
        return ResponseEntity.ok("Marked as read");
    }

    @PostMapping("/preferences/{username}/mute/{type}")
    public ResponseEntity<String> muteType(@PathVariable String username,
                                           @PathVariable String type) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.getMutedTypes().add(type);
            userRepository.save(user);
        });
        return ResponseEntity.ok("Muted " + type);
    }

    @PostMapping("/preferences/{username}/unmute/{type}")
    public ResponseEntity<String> unmuteType(@PathVariable String username,
                                             @PathVariable String type) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.getMutedTypes().remove(type);
            userRepository.save(user);
        });
        return ResponseEntity.ok("Unmuted " + type);
    }

    @GetMapping("/dlq/count")
    public ResponseEntity<Map<String, Object>> getDlqInfo() {
        return ResponseEntity.ok(Map.of(
                "message", "DLQ is active. Failed messages are logged and can be retried.",
                "topic", "notifications-dlq"
        ));
    }
    @GetMapping("/preferences/{username}")
    public ResponseEntity<?> getPreferences(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(user -> ResponseEntity.ok(user.getMutedTypes()))
                .orElse(ResponseEntity.notFound().build());
    }

//    @GetMapping("/search")
//    public ResponseEntity<List<Notification>> search(
//            @RequestParam String userId,
//            @RequestParam String q) {
//        return ResponseEntity.ok(searchService.search(userId, q, 5));
//    }
//
//    @GetMapping("/summary/{userId}")
//    public ResponseEntity<Map<String, String>> getSummary(@PathVariable String userId) {
//        List<Notification> unread = repository
//                .findByUserIdOrderByTimestampDesc(userId)
//                .stream()
//                .filter(n -> !n.isRead())
//                .limit(10)
//                .collect(Collectors.toList());
//
//        List<String> messages = unread.stream()
//                .map(n -> n.getType() + ": " + n.getMessage())
//                .collect(Collectors.toList());
//
//        String summary = summarizationService.summarize(messages);
//        return ResponseEntity.ok(Map.of("summary", summary));
//    }
@GetMapping("/search")
public ResponseEntity<?> search(
        @RequestParam String userId,
        @RequestParam String q) {
    if (isRateLimited(searchLog, userId, 10)) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Search rate limit exceeded. Max 10 per minute."));
    }
    return ResponseEntity.ok(searchService.search(userId, q, 5));
}

    @GetMapping("/summary/{userId}")
    public ResponseEntity<?> getSummary(@PathVariable String userId) {
        if (isRateLimited(summaryLog, userId, 5)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Summary rate limit exceeded. Max 5 per minute."));
        }

        List<Notification> unread = repository
                .findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .filter(n -> !n.isRead())
                .limit(10)
                .collect(Collectors.toList());

        List<String> messages = unread.stream()
                .map(n -> n.getType() + ": " + n.getMessage())
                .collect(Collectors.toList());

        String summary = summarizationService.summarize(messages);
        return ResponseEntity.ok(Map.of("summary", summary));

    }
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNotification(@PathVariable String id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.ok("Notification deleted");
    }

    @DeleteMapping("/all/{userId}")
    public ResponseEntity<String> deleteAllNotifications(@PathVariable String userId) {
        List<Notification> notifications = repository.findByUserIdOrderByTimestampDesc(userId);
        repository.deleteAll(notifications);
        return ResponseEntity.ok("All notifications deleted for " + userId);
    }
}