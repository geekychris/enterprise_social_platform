package com.social.app.controller.rest;

import com.social.app.service.ConversationService;
import com.social.app.service.MessageService;
import com.social.app.service.UnreadCountService;
import com.social.core.dto.MessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offline device catch-up endpoint.
 * Allows disconnected devices to sync up in a single request.
 */
@RestController
@RequestMapping("/api/catchup")
public class CatchUpController {

    private static final Logger log = LoggerFactory.getLogger(CatchUpController.class);

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final UnreadCountService unreadCountService;

    public CatchUpController(MessageService messageService,
                             ConversationService conversationService,
                             UnreadCountService unreadCountService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.unreadCountService = unreadCountService;
    }

    /**
     * POST /api/catchup
     * Body: { "conversations": { "convId": lastMessageId, ... } }
     * Returns: { "updates": [ { "conversationId": ..., "newMessages": [...], "unreadCount": ... } ] }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> catchUp(@RequestBody Map<String, Object> body,
                                                        Authentication auth) {
        long userId = (Long) auth.getPrincipal();

        @SuppressWarnings("unchecked")
        Map<String, Object> conversations = (Map<String, Object>) body.get("conversations");
        if (conversations == null || conversations.isEmpty()) {
            return ResponseEntity.ok(Map.of("updates", List.of()));
        }

        List<Map<String, Object>> updates = new ArrayList<>();

        for (Map.Entry<String, Object> entry : conversations.entrySet()) {
            long conversationId = Long.parseLong(entry.getKey());
            long lastMessageId = parseLong(entry.getValue());

            try {
                // Verify the user is a participant
                var participant = conversationService.verifyParticipant(conversationId, userId);

                // Get messages after the last known message
                List<MessageDto> allMessages = messageService.getMessages(
                        conversationId, participant.getVisibleFrom(), 0, 100);

                // Filter to only messages after lastMessageId
                List<MessageDto> newMessages = allMessages.stream()
                        .filter(m -> m.id() > lastMessageId)
                        .toList();

                long unreadCount;
                try {
                    unreadCount = unreadCountService.getTotalUnread(userId);
                } catch (Exception e) {
                    log.warn("Failed to get unread count from service, falling back to DB: {}", e.getMessage());
                    unreadCount = messageService.getUnreadCount(userId);
                }

                updates.add(Map.of(
                        "conversationId", conversationId,
                        "newMessages", newMessages,
                        "unreadCount", unreadCount
                ));
            } catch (IllegalArgumentException e) {
                log.warn("Skipping conversation {} in catch-up for user {}: {}", conversationId, userId, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of("updates", updates));
    }

    private static long parseLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot parse as long: " + value);
    }
}
