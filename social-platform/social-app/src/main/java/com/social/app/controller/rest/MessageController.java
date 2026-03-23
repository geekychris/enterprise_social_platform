package com.social.app.controller.rest;

import com.social.app.persistence.entity.MessageEntity;
import com.social.app.service.MessageService;
import com.social.core.dto.ConversationDto;
import com.social.core.dto.MessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

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

        MessageEntity entity = messageService.send(senderId, recipientId, content, attachmentIds);
        return ResponseEntity.ok(messageService.toDto(entity));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> getConversations(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(messageService.getConversations(userId));
    }

    @GetMapping("/conversation/{partnerId}")
    public ResponseEntity<List<MessageDto>> getConversation(@PathVariable long partnerId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size,
                                                              Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(messageService.getConversation(userId, partnerId, page, size));
    }

    @PostMapping("/conversation/{partnerId}/read")
    public ResponseEntity<Void> markRead(@PathVariable long partnerId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        messageService.markRead(userId, partnerId);
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
