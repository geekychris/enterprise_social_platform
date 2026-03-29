package com.social.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.persistence.repository.ConversationParticipantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.social.core.dto.MessageDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MessageBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ConversationParticipantRepository participantRepo;
    private final ObjectMapper objectMapper;

    public MessageBroadcastService(SimpMessagingTemplate messagingTemplate,
                                    StringRedisTemplate redisTemplate,
                                    ConversationParticipantRepository participantRepo) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.participantRepo = participantRepo;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Called after a message is saved. Broadcasts to all conversation participants
     * via WebSocket and publishes to Redis for other server instances.
     */
    public void broadcastMessage(MessageDto message, long conversationId) {
        // 1. Send via WebSocket to each participant
        var participants = participantRepo.findByConversationId(conversationId);
        for (var p : participants) {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(p.getUserId()),
                    "/queue/messages",
                    message
            );
        }

        // 2. Publish to Redis for other server instances
        String channel = "conversation:" + conversationId;
        try { redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(message)); }
        catch (Exception e) { log.warn("Redis broadcast failed: {}", e.getMessage()); }
    }

    /**
     * Broadcast a typing indicator to all subscribers of a conversation topic.
     */
    public void broadcastTyping(long conversationId, long userId, String userName) {
        messagingTemplate.convertAndSend(
                "/topic/conversation." + conversationId + ".typing",
                Map.of("userId", userId, "userName", userName)
        );
    }
}
