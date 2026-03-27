package com.social.app.controller.rest;

import com.social.app.persistence.entity.ConversationEntity;
import com.social.app.service.BotService;
import com.social.app.service.ConversationService;
import com.social.app.service.MessageService;
import com.social.core.dto.ConversationDto;
import com.social.core.dto.MessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Legacy message endpoints for backwards compatibility.
 * New code should use /api/conversations endpoints.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final BotService botService;

    public MessageController(MessageService messageService, ConversationService conversationService, BotService botService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.botService = botService;
    }

    /**
     * Legacy: send message by recipientId. Creates/reuses a DIRECT conversation.
     */
    @PostMapping
    public ResponseEntity<MessageDto> sendMessage(@RequestBody Map<String, Object> body,
                                                    Authentication auth) {
        long senderId = (Long) auth.getPrincipal();
        long recipientId = parseLong(body.get("recipientId"));
        String content = (String) body.get("content");

        List<Long> attachmentIds = List.of();
        if (body.containsKey("attachmentIds") && body.get("attachmentIds") instanceof List<?> rawList) {
            attachmentIds = rawList.stream()
                    .map(MessageController::parseLong)
                    .toList();
        }

        ConversationEntity conversation = conversationService.getOrCreateDirect(senderId, recipientId);
        var entity = messageService.send(senderId, conversation.getId(), content, attachmentIds);

        // Trigger bot if applicable
        botService.handleMessage(conversation.getId(), senderId, content);

        return ResponseEntity.ok(messageService.toDto(entity));
    }

    /**
     * Legacy: list conversations. Returns the new ConversationDto format.
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> getConversations(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(conversationService.getConversationsForUser(userId));
    }

    /**
     * Legacy: get conversation by partner ID.
     */
    @GetMapping("/conversation/{partnerId}")
    public ResponseEntity<List<MessageDto>> getConversation(@PathVariable long partnerId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size,
                                                              Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        ConversationEntity conversation = conversationService.getOrCreateDirect(userId, partnerId);
        var participant = conversationService.verifyParticipant(conversation.getId(), userId);
        return ResponseEntity.ok(messageService.getMessages(conversation.getId(), participant.getVisibleFrom(), page, size));
    }

    /**
     * Legacy: mark conversation read by partner ID.
     */
    @PostMapping("/conversation/{partnerId}/read")
    public ResponseEntity<Void> markRead(@PathVariable long partnerId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        ConversationEntity conversation = conversationService.getOrCreateDirect(userId, partnerId);
        conversationService.markRead(conversation.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        long count = messageService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    private static long parseLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot parse as long: " + value);
    }
}
