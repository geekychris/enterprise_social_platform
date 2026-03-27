package com.social.app.controller.rest;

import com.social.app.persistence.entity.MessageEntity;
import com.social.app.persistence.repository.MessageRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.app.service.BotService;
import com.social.app.service.ConversationService;
import com.social.app.service.FeedService;
import com.social.app.service.OllamaService;
import com.social.core.dto.PostDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final OllamaService ollamaService;
    private final BotService botService;
    private final MessageRepository messageRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final FeedService feedService;
    private final com.social.app.service.PostService postService;

    public AiController(OllamaService ollamaService,
                        BotService botService,
                        MessageRepository messageRepository,
                        PostRepository postRepository,
                        UserRepository userRepository,
                        ConversationService conversationService,
                        FeedService feedService,
                        com.social.app.service.PostService postService) {
        this.ollamaService = ollamaService;
        this.botService = botService;
        this.messageRepository = messageRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.feedService = feedService;
        this.postService = postService;
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody Map<String, Object> body, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        String context = (String) body.get("context");
        String question = (String) body.get("question");
        Long contextId = body.get("contextId") != null ? parseLong(body.get("contextId")) : null;

        SseEmitter emitter = new SseEmitter(120_000L);

        // Build context in a background thread so we don't block
        Thread.ofVirtual().start(() -> {
            try {
                String contextText = gatherContext(context, contextId, userId);
                String systemPrompt = buildSystemPrompt(context);
                String userMessage = contextText + "\n\nUser's question: " + question;
                ollamaService.streamChat(systemPrompt, userMessage, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    private String gatherContext(String context, Long contextId, long userId) {
        return switch (context) {
            case "conversation" -> gatherConversationContext(contextId, userId);
            case "group" -> gatherPostContext(contextId, userId, "group");
            case "page" -> gatherPostContext(contextId, userId, "page");
            case "feed" -> gatherFeedContext(userId);
            default -> throw new IllegalArgumentException("Unknown context type: " + context);
        };
    }

    private String gatherConversationContext(Long conversationId, long userId) {
        var participant = conversationService.verifyParticipant(conversationId, userId);
        List<MessageEntity> messages;
        if (participant.getVisibleFrom() != null) {
            messages = messageRepository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    conversationId, participant.getVisibleFrom(), PageRequest.of(0, 100));
        } else {
            messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 100));
        }

        // Reverse to chronological order
        var reversed = new java.util.ArrayList<>(messages);
        java.util.Collections.reverse(reversed);

        StringBuilder sb = new StringBuilder("Conversation messages:\n\n");
        for (MessageEntity msg : reversed) {
            String senderName = userRepository.findById(msg.getSenderId())
                    .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                    .orElse("Unknown");
            sb.append(senderName).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String gatherPostContext(Long targetId, long userId, String type) {
        var posts = postRepository.findByTargetIdOrderByCreatedAtDesc(targetId);
        // Take up to 50 recent posts
        var recent = posts.stream().limit(50).toList();

        StringBuilder sb = new StringBuilder("Recent " + type + " posts:\n\n");
        for (var post : recent) {
            PostDto dto = postService.toDto(post, userId);
            sb.append("--- Post by ").append(dto.author().displayName())
              .append(" (").append(dto.createdAt()).append(") ---\n")
              .append(dto.content()).append("\n")
              .append("Reactions: ").append(dto.reactionCounts()).append(", Comments: ").append(dto.commentCount())
              .append("\n\n");
        }
        return sb.toString();
    }

    private String gatherFeedContext(long userId) {
        var feedResponse = feedService.assembleFeed(userId, null, 30);
        StringBuilder sb = new StringBuilder("Recent feed posts:\n\n");
        for (PostDto post : feedResponse.posts()) {
            sb.append("--- Post by ").append(post.author().displayName())
              .append(" (").append(post.createdAt()).append(") ---\n")
              .append(post.content()).append("\n")
              .append("Reactions: ").append(post.reactionCounts()).append(", Comments: ").append(post.commentCount())
              .append("\n\n");
        }
        return sb.toString();
    }

    private String buildSystemPrompt(String context) {
        String base = "You are a helpful AI assistant integrated into a social enterprise platform called WorkSphere. " +
                "You help users understand and summarize content. Be concise and helpful. " +
                "When summarizing, highlight key themes, important decisions, and action items. ";

        return switch (context) {
            case "conversation" -> base + "You are looking at a conversation thread. " +
                    "Help the user understand the conversation, summarize it, or answer questions about what was discussed.";
            case "group" -> base + "You are looking at posts from a group. " +
                    "Help the user understand recent activity, trending topics, or key discussions in the group.";
            case "page" -> base + "You are looking at posts from a page. " +
                    "Help the user understand recent updates and content from this page.";
            case "feed" -> base + "You are looking at the user's personalized feed. " +
                    "Help the user catch up on what's happening, identify important posts, or summarize recent activity.";
            default -> base;
        };
    }

    /**
     * Returns the bot user info so clients can start conversations with it.
     */
    @GetMapping("/bot/info")
    public ResponseEntity<Map<String, Object>> getBotInfo() {
        long botId = botService.getBotUserId();
        var user = userRepository.findById(botId);
        return ResponseEntity.ok(Map.of(
                "id", botId,
                "username", user.map(u -> u.getUsername()).orElse("roid"),
                "displayName", user.map(u -> u.getDisplayName()).orElse("Roid"),
                "avatarUrl", user.map(u -> u.getAvatarUrl()).orElse("")
        ));
    }

    private static long parseLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot parse as long: " + value);
    }
}
