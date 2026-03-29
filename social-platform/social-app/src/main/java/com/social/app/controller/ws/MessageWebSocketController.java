package com.social.app.controller.ws;

import com.social.app.service.ConversationService;
import com.social.app.service.MessageBroadcastService;
import com.social.app.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class MessageWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(MessageWebSocketController.class);

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final MessageBroadcastService broadcastService;

    public MessageWebSocketController(MessageService messageService,
                                       ConversationService conversationService,
                                       MessageBroadcastService broadcastService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.broadcastService = broadcastService;
    }

    @MessageMapping("/send/{conversationId}")
    public void sendMessage(@DestinationVariable long conversationId,
                            @Payload Map<String, String> payload,
                            SimpMessageHeaderAccessor headerAccessor) {
        long userId = getUserId(headerAccessor);
        String content = payload.get("content");

        conversationService.verifyParticipant(conversationId, userId);
        var entity = messageService.send(userId, conversationId, content, null);
        // Broadcast happens inside messageService.send now
    }

    @MessageMapping("/typing/{conversationId}")
    public void typing(@DestinationVariable long conversationId,
                       SimpMessageHeaderAccessor headerAccessor) {
        long userId = getUserId(headerAccessor);
        try {
            broadcastService.broadcastTyping(conversationId, userId, "User");
        } catch (Exception e) {
            log.warn("Failed to broadcast typing indicator for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    private long getUserId(SimpMessageHeaderAccessor accessor) {
        Object userId = accessor.getSessionAttributes().get("userId");
        if (userId instanceof Long) return (Long) userId;
        if (userId instanceof String) return Long.parseLong((String) userId);
        throw new IllegalStateException("No userId in WebSocket session");
    }
}
