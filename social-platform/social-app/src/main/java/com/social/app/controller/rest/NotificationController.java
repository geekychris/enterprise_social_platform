package com.social.app.controller.rest;

import com.social.app.service.NotificationService;
import com.social.app.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getNotifications(
            @RequestParam(defaultValue = "30") int limit,
            Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var notifications = notificationService.getNotifications(userId, limit);

        List<Map<String, Object>> result = notifications.stream().map(n -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", n.getId());
            map.put("type", n.getType());
            map.put("message", n.getMessage());
            map.put("read", n.isRead());
            map.put("targetId", n.getTargetId());
            map.put("targetType", n.getTargetType());
            map.put("createdAt", n.getCreatedAt());
            if (n.getActorId() != null) {
                userService.getById(n.getActorId()).ifPresent(actor -> {
                    map.put("actorId", actor.getId());
                    map.put("actorName", actor.getDisplayName() != null ? actor.getDisplayName() : actor.getUsername());
                    map.put("actorAvatarUrl", actor.getAvatarUrl());
                });
            }
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PostMapping("/mark-read")
    public ResponseEntity<Void> markRead(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }
}
