package com.social.app.controller.rest;

import com.social.app.persistence.entity.ConversationEntity;
import com.social.app.service.BotService;
import com.social.app.service.ConversationService;
import com.social.app.service.MessageService;
import com.social.core.dto.ConversationDto;
import com.social.core.dto.CreateConversationRequest;
import com.social.core.dto.MessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final BotService botService;

    public ConversationController(ConversationService conversationService, MessageService messageService, BotService botService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.botService = botService;
    }

    @PostMapping
    public ResponseEntity<ConversationDto> create(@RequestBody CreateConversationRequest request,
                                                   Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        ConversationEntity entity = conversationService.create(userId, request.participantIds(), request.name());
        return ResponseEntity.ok(conversationService.getConversation(entity.getId(), userId));
    }

    /**
     * Get or create a direct conversation with a specific user.
     */
    @PostMapping("/direct/{targetUserId}")
    public ResponseEntity<ConversationDto> getOrCreateDirect(@PathVariable long targetUserId,
                                                              Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        ConversationEntity entity = conversationService.getOrCreateDirect(userId, targetUserId);
        return ResponseEntity.ok(conversationService.getConversation(entity.getId(), userId));
    }

    @GetMapping
    public ResponseEntity<List<ConversationDto>> list(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(conversationService.getConversationsForUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDto> get(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(conversationService.getConversation(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationDto> rename(@PathVariable long id,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        conversationService.rename(id, userId, body.get("name"));
        return ResponseEntity.ok(conversationService.getConversation(id, userId));
    }

    @PostMapping("/{id}/participants")
    public ResponseEntity<ConversationDto> addParticipant(@PathVariable long id,
                                                @RequestBody Map<String, Object> body,
                                                Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        long targetUserId = parseLong(body.get("userId"));
        boolean shareHistory = body.containsKey("shareHistory") && Boolean.TRUE.equals(body.get("shareHistory"));
        conversationService.addParticipant(id, userId, targetUserId, shareHistory);
        return ResponseEntity.ok(conversationService.getConversation(id, userId));
    }

    @DeleteMapping("/{id}/participants/{targetUserId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable long id,
                                                   @PathVariable long targetUserId,
                                                   Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        conversationService.removeParticipant(id, userId, targetUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable long id,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size,
                                                         Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var participant = conversationService.verifyParticipant(id, userId);
        return ResponseEntity.ok(messageService.getMessages(id, participant.getVisibleFrom(), page, size));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageDto> sendMessage(@PathVariable long id,
                                                    @RequestBody Map<String, Object> body,
                                                    Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        conversationService.verifyParticipant(id, userId);

        String content = (String) body.get("content");
        List<Long> attachmentIds = List.of();
        if (body.containsKey("attachmentIds") && body.get("attachmentIds") instanceof List<?> rawList) {
            attachmentIds = rawList.stream()
                    .map(ConversationController::parseLong)
                    .toList();
        }

        var entity = messageService.send(userId, id, content, attachmentIds);

        // Trigger bot if applicable
        botService.handleMessage(id, userId, content);

        return ResponseEntity.ok(messageService.toDto(entity));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        conversationService.markRead(id, userId);
        return ResponseEntity.ok().build();
    }

    private static long parseLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot parse as long: " + value);
    }
}
